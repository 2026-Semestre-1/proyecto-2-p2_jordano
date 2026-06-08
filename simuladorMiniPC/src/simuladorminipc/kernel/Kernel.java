package simuladorminipc.kernel;

import simuladorminipc.assembler.Assembler;
import simuladorminipc.assembler.Instruction;
import simuladorminipc.clock.SystemClock;
import simuladorminipc.cpu.CPU;
import simuladorminipc.cpu.CycleEvent;
import simuladorminipc.cpu.CycleResult;
import simuladorminipc.cpu.Dispatcher;
import simuladorminipc.interrupt.Interrupt;
import simuladorminipc.interrupt.InterruptManager;
import simuladorminipc.interrupt.InterruptType;
import simuladorminipc.io.IOManager;
import simuladorminipc.memory.MemoryManager;
import simuladorminipc.memory.RAM;
import simuladorminipc.memory.VirtualMemoryManager;
import simuladorminipc.memory.SwapManager;
import simuladorminipc.model.PCB;
import simuladorminipc.model.ProcessState;
import simuladorminipc.process.ProcessManager;
import simuladorminipc.process.QueueManager;
import simuladorminipc.process.StateManager;
import simuladorminipc.scheduler.SchedulingPolicyManager;
import simuladorminipc.scheduler.SchedulingPolicyManager.Policy;
import simuladorminipc.stats.StatisticsManager;
import simuladorminipc.storage.Disk;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Central Kernel orchestrator.
 * <p>
 * Owns every subsystem and drives the simulation cycle by cycle.
 * The public interface exposed to the GUI is:
 * <ul>
 *   <li>{@link #boot()} – initialise all subsystems.</li>
 *   <li>{@link #loadProgram(File, int, int)} – parse a .asm file and create a PCB.</li>
 *   <li>{@link #executeTick()} – advance the simulation by one CPU second.</li>
 *   <li>{@link #allProcessesFinished()} – termination guard.</li>
 *   <li>{@link #provideKeyboardInput(PCB, int)} – deliver keyboard value after INT 09H.</li>
 * </ul>
 * </p>
 *
 * <h3>executeTick() cycle</h3>
 * <ol>
 *   <li>Advance system clock.</li>
 *   <li>Load arriving processes (admission from newQueue).</li>
 *   <li>Tick I/O devices; unblock completed I/O processes.</li>
 *   <li>Invoke scheduler; dispatch selected process if CPU is idle.</li>
 *   <li>Execute one CPU cycle.</li>
 *   <li>Handle CycleEvent (finish, screen, keyboard, file, error).</li>
 *   <li>Check quantum expiry for Round-Robin.</li>
 *   <li>Update statistics.</li>
 *   <li>Notify listeners.</li>
 * </ol>
 */
public class Kernel {

    // ── Subsystems ────────────────────────────────────────────────────────────

    private final CPU                   cpu;
    private final Dispatcher            dispatcher;
    private final SystemClock           clock;
    private final QueueManager          queueManager;
    private final StateManager          stateManager;
    private final ProcessManager        processManager;
    private final SchedulingPolicyManager policyManager;
    private final InterruptManager      interruptManager;
    private final RAM                   ram;
    private final MemoryManager         memoryManager;
    private final VirtualMemoryManager  vmManager;
    private final SwapManager           swapManager;
    private final Disk                  disk;
    private final IOManager             ioManager;
    private final StatisticsManager     statisticsManager;

    // ── State ─────────────────────────────────────────────────────────────────

    /** Tracks how many file-index entries have been written to RAM cells 20–29. */
    private int fileIndexSlot = 0;

    private final List<KernelEventListener> listeners   = new ArrayList<>();
    private       boolean                   booted      = false;   //Control to prevent ticks before boot; set on boot, cleared on reset
    private       boolean                   halted      = false;   //Control to prevent ticks after all processes finished; reset on new load
    private       PCB                       waitingForKeyboard = null;

    // ── Constructor ───────────────────────────────────────────────────────────

    public Kernel(int ramSize, int diskSize) {
        cpu              = new CPU(1);                      // 1 cycle per instruction for simplicity
        dispatcher       = new Dispatcher();                // Dispatcher is separate from CPU for better separation of concerns and statistics tracking
        clock            = new SystemClock();               // System clock is a separate subsystem to allow listeners to query the current tick without needing CPU access
        queueManager     = new QueueManager();              // Centralised queue manager for better visibility and easier statistics tracking
        stateManager     = new StateManager(queueManager);  // State manager needs queue manager to move PCBs between queues atomically
        ram              = new RAM(ramSize);                // RAM and Disk are separate subsystems to allow for more complex memory management and swapping policies in the future
        disk             = new Disk(diskSize);              // Disk also simulates persistent storage for program files, separate from RAM
        memoryManager    = new MemoryManager(ram);          // Memory manager is separate from RAM to encapsulate allocation logic and allow for more complex policies in the future
        swapManager      = new SwapManager();               // Swap manager is separate; virtual memory overflow always targets disk (swap), not RAM
        vmManager        = new VirtualMemoryManager(memoryManager, swapManager); // Virtual memory maps to RAM (resident pages) and disk via SwapManager (non-resident pages)
        ioManager        = new IOManager(disk);            // IO manager is separate to handle input/output operations independently
        processManager   = new ProcessManager(queueManager, stateManager, memoryManager); // Process manager is separate to handle process lifecycle management
        policyManager    = new SchedulingPolicyManager(Policy.FCFS, 2); // Policy manager is separate to manage scheduling policies
        interruptManager = new InterruptManager();         // Interrupt manager is separate to handle interrupts independently
        statisticsManager= new StatisticsManager(queueManager, cpu); // Statistics manager is separate to collect and manage system statistics
    }

    /**
     * Constructs a Kernel with default RAM and disk sizes (512 cells each).
     */
    public Kernel() { this(512, 512); }

    // ── Boot ─────────────────────────────────────────────────────────────────

    /**
     * Boots the kernel: resets the system clock and PID counter, and sets
     * the internal state to running. Must be called before any call to
     * {@link #executeTick()} or {@link #loadProgram(java.io.File, int, int)}.
     */
    public void boot() {
        clock.reset();
        PCB.resetPidCounter();
        booted  = true;
        halted  = false;
        waitingForKeyboard = null;
    }

    // ── Program loading ───────────────────────────────────────────────────────

    /**
     * Parses a .asm source file, creates a PCB and enqueues it in the new queue.
     *
     * @param file          source .asm file
     * @param arrivalTime   tick at which the process should be admitted
     * @param priority      scheduling priority (lower = higher priority)
     * @return the created PCB
     * @throws Exception if the file cannot be parsed
     */
    public PCB loadProgram(File file, int arrivalTime, int priority) throws Exception {
        List<Instruction> instructions = Assembler.parse(file);
        int memNeeded = instructions.size() + 5;  // instructions + some data headroom

        PCB pcb = new PCB(file.getName().replace(".asm",""), arrivalTime, priority, memNeeded);
        pcb.setInstructions(instructions);

        // Store a copy of the program on disk
        disk.storeProgram(pcb.getName(), instructions);

        // Write file index entry into the RAM OS area (cells FILE_INDEX_BASE..FILE_INDEX_BASE+9)
        writeFileIndexEntry(pcb.getName());

        processManager.submit(pcb);
        return pcb;
    }

    /**
     * Creates and enqueues a PCB for an already-parsed list of instructions.
     * Used programmatically (e.g. from a demo or test harness).
     *
     * @param name         process name
     * @param instructions pre-parsed instruction list
     * @param arrivalTime  tick at which the process should be admitted
     * @param priority     scheduling priority (lower = higher priority)
     * @return the newly created PCB
     */
    public PCB loadProcess(String name, List<Instruction> instructions,
                           int arrivalTime, int priority) {
        int memNeeded = instructions.size() + 5;
        PCB pcb = new PCB(name, arrivalTime, priority, memNeeded);
        pcb.setInstructions(instructions);
        disk.storeProgram(name, instructions);
        writeFileIndexEntry(name);
        processManager.submit(pcb);
        return pcb;
    }

    /**
     * Writes a file-index entry for {@code programName} into the OS-reserved
     * RAM area (cells {@link RAM#FILE_INDEX_BASE} … FILE_INDEX_BASE + FILE_INDEX_SIZE - 1).
     * Each entry has the format {@code "IDX:<name>@<diskAddr>"}.
     * Silently ignores overflow if more than {@link RAM#FILE_INDEX_SIZE} files are loaded.
     */
    private void writeFileIndexEntry(String programName) {
        if (fileIndexSlot >= RAM.FILE_INDEX_SIZE) return;
        simuladorminipc.storage.DiskFile df = disk.getDirectory().get(programName);
        int diskAddr = (df != null) ? df.getStartAddress() : -1;
        int cell = RAM.FILE_INDEX_BASE + fileIndexSlot;
        ram.write(cell, "IDX:" + programName + "@" + diskAddr);
        fileIndexSlot++;
    }

    // ── Main simulation tick ──────────────────────────────────────────────────

    /**
     * Performs one complete OS simulation cycle (one CPU second).
     * <p>
     * The execution order within a tick follows the OS theory pipeline:
     * clock advance → process admission → I/O tick → scheduling → CPU cycle →
     * interrupt handling → quantum check → statistics update → listener notification.
     * </p>
     * <p>This method is a no-op if the kernel has not been booted, has halted, or is
     * currently waiting for keyboard input from the user (INT 09H).</p>
     */
    public void executeTick() {
        if (!booted || halted) return;
        // If waiting for keyboard, don't advance – GUI must provide input first
        if (waitingForKeyboard != null) return;

        // 1. Advance clock
        long tick = clock.tick();

        // 2. Load arriving processes into memory
        processManager.loadArrivingProcesses(tick);

        // 3. Tick I/O devices; unblock completed processes
        List<PCB> unblocked = ioManager.tick();
        for (PCB p : unblocked) {
            ProcessState old = p.getState();
            stateManager.unblockToReady(p);
            fireStateChanged(p, old, p.getState());
            interruptManager.raise(new Interrupt(InterruptType.IO_FINISHED, p));
        }

        // 4. Schedule & dispatch
        scheduleAndDispatch(tick);

        // 5. Execute CPU cycle
        CycleEvent event = cpu.executeCycle();

        // 6. Handle cycle event
        PCB running = cpu.getCurrentProcess();
        handleCycleEvent(event, running, tick);

        // 7. Check quantum expiry (Round-Robin)
        if (running != null && policyManager.getCurrentPolicy() == Policy.ROUND_ROBIN) {
            running.decrementQuantumRemaining();
            if (running.getQuantumRemaining() <= 0
                    && event.getResult() == CycleResult.NORMAL) {
                interruptManager.raise(new Interrupt(InterruptType.QUANTUM_EXPIRED, running));
                handleQuantumExpiry(running);
            }
        }

        // 8. Update statistics
        statisticsManager.setContextSwitches(dispatcher.getContextSwitches());
        statisticsManager.onTick(tick);

        // 8b. Refresh BCP data in OS area for all in-memory processes
        for (PCB p : queueManager.getAllLive()) {
            memoryManager.updateBcpOsArea(p);
        }
        PCB afterEvent = cpu.getCurrentProcess();
        if (afterEvent != null) memoryManager.updateBcpOsArea(afterEvent);

        // 9. Notify listeners
        fireTickCompleted(tick, cpu.getCurrentProcess());

        // 10. Check if simulation is done
        if (processManager.allFinished() && cpu.isIdle()) {
            halted = true;
            fireAllFinished();
        }
    }

    // ── Scheduling & dispatch ─────────────────────────────────────────────────

    /**
     * Selects and dispatches a process from the ready queue for this tick.
     * Preempts the currently running process if the scheduler returns a different one.
     *
     * @param tick current system clock value (used for response-time tracking)
     */
    private void scheduleAndDispatch(long tick) {
        List<PCB> ready = queueManager.getReadyQueue();
        PCB current = cpu.getCurrentProcess();

        PCB next = policyManager.selectNext(ready, current);
        if (next == null) return;
        if (next == current) return;

        // Preempt current if needed
        if (current != null) {
            ProcessState old = current.getState();
            stateManager.preemptToReady(current);
            fireStateChanged(current, old, current.getState());
        }

        // First-run response time
        statisticsManager.onProcessFirstRun(next, tick);
        if (!next.isStarted()) {
            next.setStarted(true);
        }

        // Set quantum for RR
        if (policyManager.getCurrentPolicy() == Policy.ROUND_ROBIN) {
            next.setQuantumRemaining(policyManager.getRrQuantum());
        }

        stateManager.setRunning(next);
        dispatcher.dispatch(next, cpu);
        fireStateChanged(next, ProcessState.READY, ProcessState.RUNNING);
    }

    /**
     * Handles quantum expiry for Round-Robin: removes the running process from
     * the CPU and places it back at the end of the ready queue.
     *
     * @param process the process whose quantum has expired
     */
    private void handleQuantumExpiry(PCB process) {
        if (process == null) return;
        ProcessState old = process.getState();
        cpu.releaseProcess();
        stateManager.preemptToReady(process);
        fireStateChanged(process, old, process.getState());
    }

    // ── CycleEvent handler ────────────────────────────────────────────────────

    /**
     * Reacts to the {@link simuladorminipc.cpu.CycleEvent} returned by the CPU
     * after each tick. Routes to the appropriate subsystem (I/O, file system,
     * statistics) and fires the matching listener callbacks.
     *
     * @param event   the event produced by {@link simuladorminipc.cpu.CPU#executeCycle()}
     * @param process the process that was running when the event was produced (may be null)
     * @param tick    current tick number (needed for statistics timestamps)
     */
    private void handleCycleEvent(CycleEvent event, PCB process, long tick) {
        if (process == null) return;

        switch (event.getResult()) {

            case PROCESS_FINISHED:
                cpu.releaseProcess();
                ProcessState old1 = process.getState();
                stateManager.terminate(process);
                fireStateChanged(process, old1, process.getState());
                statisticsManager.onProcessFinished(process, tick);
                memoryManager.free(process);
                fireProcessFinished(process);
                break;

            case SCREEN_OUTPUT:
                fireScreenOutput(event.getScreenText());
                break;

            case KEYBOARD_INPUT:
                // Block the process, remember it, ask the GUI
                cpu.releaseProcess();
                ProcessState old2 = process.getState();
                stateManager.blockOnIO(process);
                ioManager.submitKeyboardRequest(process);
                fireStateChanged(process, old2, process.getState());
                waitingForKeyboard = process;
                fireKeyboardInputRequired(process);
                break;

            case FILE_OPERATION:
                cpu.releaseProcess();
                ProcessState old3 = process.getState();
                stateManager.blockOnIO(process);
                ioManager.submitFileRequest(process,
                    event.getFileSubcode(), event.getFilename());
                fireStateChanged(process, old3, process.getState());
                break;

            case ERROR:
                fireExecutionError(process, event.getErrorMessage());
                // Terminate the offending process gracefully
                cpu.releaseProcess();
                ProcessState old4 = process.getState();
                stateManager.terminate(process);
                fireStateChanged(process, old4, process.getState());
                statisticsManager.onProcessFinished(process, tick);
                memoryManager.free(process);
                break;

            case IDLE:
            case NORMAL:
            default:
                break;
        }
    }

    // ── Keyboard input delivery ───────────────────────────────────────────────

    /**
     * Called by the GUI after the user enters a value in response to INT 09H.
     *
     * @param process   the blocked PCB that issued the keyboard request
     * @param value     numeric value entered by the user (0–255)
     */
    public void provideKeyboardInput(PCB process, int value) {
        if (process == null || waitingForKeyboard != process) return;
        ioManager.completeKeyboardRequest(process, value);
        ProcessState old = process.getState();
        stateManager.unblockToReady(process);
        fireStateChanged(process, old, process.getState());
        waitingForKeyboard = null;
    }

    // ── Event firing ─────────────────────────────────────────────────────────

    public void addListener(KernelEventListener l)    { listeners.add(l); }
    public void removeListener(KernelEventListener l) { listeners.remove(l); }

    // ── Event fire helpers ────────────────────────────────────────────────────

    /**
     * Broadcasts the tick-completed event to all registered listeners.
     *
     * @param tick    the tick that just completed
     * @param running the process currently on the CPU, or {@code null} if idle
     */
    private void fireTickCompleted(long tick, PCB running) {
        for (KernelEventListener l : listeners) l.onTickCompleted(tick, running);
    }
    /**
     * Broadcasts a process state change to all registered listeners.
     *
     * @param p   the process whose state changed
     * @param old previous state
     * @param nw  new state
     */
    private void fireStateChanged(PCB p, ProcessState old, ProcessState nw) {
        for (KernelEventListener l : listeners) l.onProcessStateChanged(p, old, nw);
    }
    /**
     * Broadcasts screen output text (INT 10H) to all registered listeners.
     *
     * @param text the text to display
     */
    private void fireScreenOutput(String text) {
        for (KernelEventListener l : listeners) l.onScreenOutput(text);
    }
    /**
     * Notifies all listeners that a process is now blocked waiting for keyboard input.
     *
     * @param p the blocked process
     */
    private void fireKeyboardInputRequired(PCB p) {
        for (KernelEventListener l : listeners) l.onKeyboardInputRequired(p);
    }
    /**
     * Notifies all listeners that a process has terminated.
     *
     * @param p the terminated process
     */
    private void fireProcessFinished(PCB p) {
        for (KernelEventListener l : listeners) l.onProcessFinished(p);
    }
    /**
     * Notifies all listeners that all processes have finished and provides the
     * final statistics snapshot.
     */
    private void fireAllFinished() {
        for (KernelEventListener l : listeners) l.onAllProcessesFinished(statisticsManager);
    }
    /**
     * Notifies all listeners of a runtime execution error.
     *
     * @param p   the process that caused the error
     * @param msg human-readable error description
     */
    private void fireExecutionError(PCB p, String msg) {
        for (KernelEventListener l : listeners) l.onExecutionError(p, msg);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if all submitted processes have terminated and the CPU
     * is idle (i.e., the simulation is complete).
     *
     * @return {@code true} when the simulation has finished
     */
    public boolean allProcessesFinished()  { return halted; }
    /** Returns {@code true} when a process is blocked waiting for user keyboard input (INT 09H). */
    public boolean isWaitingForKeyboard()  { return waitingForKeyboard != null; }

    /** Returns the PCB currently blocked on keyboard input, or {@code null} if none. */
    public PCB     getWaitingForKeyboard() { return waitingForKeyboard; }

    /**
     * Triggers an admission pass without advancing the clock.
     * Used immediately after loading new program files so they are moved
     * into memory and the ready queue before the first tick.
     */
    public void admitWaitingProcesses() {
        processManager.loadArrivingProcesses(clock.getCurrentTick());
    }

    public CPU                    getCpu()              { return cpu; }
    public SystemClock            getClock()            { return clock; }
    public QueueManager           getQueueManager()     { return queueManager; }
    public SchedulingPolicyManager getPolicyManager()   { return policyManager; }
    public MemoryManager          getMemoryManager()    { return memoryManager; }
    public VirtualMemoryManager   getVmManager()        { return vmManager; }
    public SwapManager            getSwapManager()      { return swapManager; }
    public Disk                   getDisk()             { return disk; }
    public IOManager              getIoManager()        { return ioManager; }
    public StatisticsManager      getStatisticsManager(){ return statisticsManager; }
    public RAM                    getRam()              { return ram; }
    public boolean                isBooted()            { return booted; }
    public boolean                isHalted()            { return halted; }

    /**
     * Clears the halted flag so execution can resume after new programs are
     * loaded following a completed simulation (without a full reset).
     */
    public void clearHalt() { halted = false; }

    /**
     * Changes the active scheduling policy at runtime.
     *
     * @param policy the new scheduling policy to apply
     */
    public void setSchedulingPolicy(Policy policy)    { policyManager.setPolicy(policy); }

    /**
     * Sets the quantum size for Round-Robin scheduling.
     * Has no effect when the active policy is not ROUND_ROBIN.
     *
     * @param quantum number of CPU ticks per quantum (≥ 1)
     */
    public void setRoundRobinQuantum(int quantum)     { policyManager.setRoundRobinQuantum(quantum); }

    /**
     * Resets the entire kernel state, clearing all queues and RAM.
     * Must call {@link #boot()} again after reset.
     */
    public void reset() {
        // Stop auto-run (no-op here; controller already stops it)
        cpu.releaseProcess();

        // Clear all process queues directly (getAllLive() returns a copy, cannot clear via it)
        queueManager.getNewQueue().clear();
        queueManager.getReadyQueue().clear();
        queueManager.getBlockedQueue().clear();
        queueManager.getSuspendedQueue().clear();
        queueManager.getTerminatedQueue().clear();

        // Clear memory, disk, interrupts, I/O
        ram.clear();
        disk.clear();
        interruptManager.clear();
        ioManager.reset();

        // Reset allocator state so next allocations start fresh
        memoryManager.reset();

        booted             = false;
        halted             = false;
        waitingForKeyboard = null;
        fileIndexSlot      = 0;
    }
}
