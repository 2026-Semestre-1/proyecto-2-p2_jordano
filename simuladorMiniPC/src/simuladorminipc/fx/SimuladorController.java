package simuladorminipc.fx;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;

import simuladorminipc.kernel.Kernel;
import simuladorminipc.kernel.KernelEventListener;
import simuladorminipc.model.PCB;
import simuladorminipc.model.ProcessState;
import simuladorminipc.model.RegisterSet;
import simuladorminipc.scheduler.SchedulingPolicyManager.Policy;
import simuladorminipc.stats.StatisticsManager;
import simuladorminipc.memory.RAM;
import simuladorminipc.memory.MemoryConfig;
import simuladorminipc.storage.DiskConfig;
import simuladorminipc.storage.Disk;
import simuladorminipc.storage.DiskFile;

import java.io.File;
import java.util.*;

/**
 * MVC Controller for the JavaFX OS Simulator GUI.
 * <p>
 * Bridges the {@link Kernel} (Model) with the {@link SimuladorApp} (View).
 * All Kernel event callbacks are marshalled onto the JavaFX Application Thread
 * via {@link Platform#runLater}.
 * </p>
 */
public class SimuladorController implements KernelEventListener {

    // ── Model ─────────────────────────────────────────────────────────────────

    private final Kernel kernel;

    // ── Observable state (bound to View) ──────────────────────────────────────

    /** Current system clock tick. */
    private final LongProperty currentTick = new SimpleLongProperty(0);

    /** Name/label of the active scheduling policy. */
    private final StringProperty policyLabel = new SimpleStringProperty("FCFS");

    /** True while auto-run timer is active. */
    private final BooleanProperty autoRunning = new SimpleBooleanProperty(false);

    /** True when the simulation has finished (all processes done). */
    private final BooleanProperty finished = new SimpleBooleanProperty(false);

    /** True when the simulation is waiting for keyboard input from the user. */
    private final BooleanProperty waitingKeyboard = new SimpleBooleanProperty(false);
    /** True if auto/single-proc run was running when INT 09H interrupted it. */
    private boolean kbInterruptedAuto = false;

    /** Lines in the event / screen log. */
    private final ObservableList<String> eventLog =
        FXCollections.observableArrayList();

    // -- CPU state (bound to the CPU panel labels) ----------------------------

    private final StringProperty cpuStatus    = new SimpleStringProperty("IDLE");
    private final StringProperty cpuProcess   = new SimpleStringProperty("—");
    private final StringProperty regPC        = new SimpleStringProperty("0");
    private final StringProperty regIR        = new SimpleStringProperty("0");
    private final StringProperty regAC        = new SimpleStringProperty("0");
    private final StringProperty regAX        = new SimpleStringProperty("0");
    private final StringProperty regBX        = new SimpleStringProperty("0");
    private final StringProperty regCX        = new SimpleStringProperty("0");
    private final StringProperty regDX        = new SimpleStringProperty("0");
    private final StringProperty regAH        = new SimpleStringProperty("0");
    private final StringProperty regAL        = new SimpleStringProperty("0");
    private final StringProperty stackInfo    = new SimpleStringProperty("depth=0/5");
    private final StringProperty cpuBurstInfo = new SimpleStringProperty("burst=0 rem=0");

    // -- Process queue tables (POJOs, refreshed each tick) --------------------

    private final ObservableList<ProcessRow> newQueueRows       =
        FXCollections.observableArrayList();
    private final ObservableList<ProcessRow> readyQueueRows     =
        FXCollections.observableArrayList();
    private final ObservableList<ProcessRow> blockedQueueRows   =
        FXCollections.observableArrayList();
    private final ObservableList<ProcessRow> terminatedQueueRows =
        FXCollections.observableArrayList();

    // -- Memory map (rows: address -> value) ----------------------------------

    private final ObservableList<MemoryRow> memoryRows =
        FXCollections.observableArrayList();

    // -- Disk map (rows: address -> zone -> value) ----------------------------

    private final ObservableList<DiskRow> diskRows =
        FXCollections.observableArrayList();

    // -- Disk file index (one row per stored file) ----------------------------

    private final ObservableList<DiskFileRow> diskFileRows =
        FXCollections.observableArrayList();

    // -- Live statistics properties -------------------------------------------

    private final StringProperty statCpuUtil    = new SimpleStringProperty("0.0%");
    private final StringProperty statThroughput = new SimpleStringProperty("0.0000");
    private final StringProperty statAvgWait    = new SimpleStringProperty("0.00");
    private final StringProperty statAvgTurn    = new SimpleStringProperty("0.00");
    private final StringProperty statAvgResp    = new SimpleStringProperty("0.00");
    private final StringProperty statCtxSw      = new SimpleStringProperty("0");
    private final StringProperty statTotal      = new SimpleStringProperty("0");

    // ── Auto-run timeline ─────────────────────────────────────────────────────

    private static final int AUTO_DELAY_MS = 400;
    private final Timeline autoTimeline;

    /** True while single-process auto-run (run-to-next-finish) is active. */
    private final BooleanProperty singleProcRunning = new SimpleBooleanProperty(false);
    private int singleProcBaseTerminated = 0;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
    * Constructs the controller, instantiates the {@link Kernel} with the
    * settings from {@link simuladorminipc.memory.MemoryConfig} and
    * {@link simuladorminipc.storage.DiskConfig}, registers
     * itself as a {@link KernelEventListener}, boots the kernel, and seeds
     * the observable memory/disk views.
     */
    public SimuladorController() {
        MemoryConfig memCfg = MemoryConfig.load();
        DiskConfig diskCfg = DiskConfig.load();
        kernel = new Kernel(memCfg.ramSize, diskCfg.diskSize);
        kernel.addListener(this);
        kernel.boot();

        log("[SIM] Config: RAM=" + memCfg.ramSize
            + " celdas  |  Disco=" + diskCfg.diskSize
            + " celdas  |  VMem/proceso=" + diskCfg.virtualMemorySize + " palabras");

        autoTimeline = new Timeline(
            new KeyFrame(Duration.millis(AUTO_DELAY_MS), e -> step()));
        autoTimeline.setCycleCount(Animation.INDEFINITE);

        // Seed memory view
        refreshMemory();
        refreshDisk();
    }

    // ── Public simulation commands (called by View) ──────────────────────────

    /**
     * Starts single-process auto-run mode.
     * The simulation ticks until one additional process has terminated.
     */
    public void startSingleProcess() {
        singleProcBaseTerminated = kernel.getQueueManager().getTerminatedQueue().size();
        singleProcRunning.set(true);
        if (autoTimeline.getStatus() != Animation.Status.RUNNING) {
            autoTimeline.play();
            autoRunning.set(true);
        }
    }

    /** Stops single-process auto-run mode and pauses the timeline. */
    public void stopSingleProcess() {
        singleProcRunning.set(false);
        stopAuto();
    }

    /**
     * Assembles and loads one or more {@code .asm} files into the kernel.
     * Triggers an initial memory-admission pass so processes move to READY
     * before the first tick.
     *
     * @param files list of {@code .asm} source files to load
     * @return list of error messages (empty if all files were loaded successfully)
     */
    public List<String> loadFiles(List<File> files) {
        List<String> errors = new ArrayList<>();
        int loaded = 0;
        int arrivalTick = (int) kernel.getClock().getCurrentTick();
        for (int i = 0; i < files.size(); i++) {
            try {
                // Processes inherit the current simulation tick when loaded so
                // the UI reflects their real arrival time.
                // priority = order they were selected (lower index = higher priority)
                kernel.loadProgram(files.get(i), arrivalTick, i + 1);
                loaded++;
            } catch (Exception ex) {
                errors.add(files.get(i).getName() + ": " + ex.getMessage());
            }
        }
        if (loaded > 0) {
            log("[SIM] " + loaded + " programa(s) cargado(s).");
            // Clear halted so a re-load after a completed simulation allows
            // execution without requiring a full Reset.
            kernel.clearHalt();
            // Trigger one admission pass so processes move NEW -> READY and get
            // allocated in RAM before the user presses Start.
            kernel.admitWaitingProcesses();
            refreshQueues();
            refreshMemory();
            refreshDisk();
            refreshStats();
            finished.set(false);
        }
        return errors;
    }

    /**
     * Advances the simulation by exactly one kernel tick.
     * Stops auto-run if all processes have finished or the CPU is waiting
     * for keyboard input.
     */
    public void step() {
        if (kernel.allProcessesFinished() || kernel.isWaitingForKeyboard()) {
            if (autoTimeline.getStatus() == Animation.Status.RUNNING) stopAuto();
            // If halted (not just waiting for keyboard), also reset single-proc
            // mode so the button doesn't stay stuck in "⏸ Pausar" state.
            if (kernel.allProcessesFinished() && singleProcRunning.get()) {
                stopSingleProcess();
            }
            return;
        }
        kernel.executeTick();
    }

    /** Toggles between auto-run (continuous ticking) and paused states. */
    public void toggleAuto() {
        if (autoTimeline.getStatus() == Animation.Status.RUNNING) {
            stopAuto();
        } else {
            startAuto();
        }
    }

    private void startAuto() {
        autoTimeline.play();
        autoRunning.set(true);
    }

    /** Stops the auto-run timeline and clears the running flag. */
    public void stopAuto() {
        autoTimeline.stop();
        autoRunning.set(false);
    }

    /**
     * Resets the entire simulation to its initial state:
     * stops automation, resets the kernel, clears all observable lists and counters.
     */
    public void reset() {
        stopAuto();
        kernel.reset();
        kernel.boot();
        currentTick.set(0);
        finished.set(false);
        waitingKeyboard.set(false);
        cpuStatus.set("IDLE");
        cpuProcess.set("—");
        clearRegisters();
        newQueueRows.clear();
        readyQueueRows.clear();
        blockedQueueRows.clear();
        terminatedQueueRows.clear();
        eventLog.clear();
        refreshMemory();
        refreshDisk();
        refreshStats();
        log("[SIM] Simulacion reiniciada.");
    }

    /**
     * Delivers keyboard input to the process currently blocked on INT 09H.
     *
     * @param text user-entered string; must be a decimal integer in [0, 255]
     * @return {@code true} if the value was valid and delivered; {@code false} otherwise
     */
    public boolean provideKeyboardInput(String text) {
        try {
            int val = Integer.parseInt(text.trim());
            if (val < 0 || val > 255) throw new NumberFormatException("rango");
            PCB waiting = kernel.getWaitingForKeyboard();
            kernel.provideKeyboardInput(waiting, val);
            waitingKeyboard.set(false);
            log("[KB] Valor recibido: " + val);
            // Resume auto/single-proc run if it was active before keyboard blocked
            if (kbInterruptedAuto) {
                kbInterruptedAuto = false;
                autoTimeline.play();
                autoRunning.set(true);
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Changes the active scheduling policy at runtime.
     *
     * @param p new scheduling policy
     */
    public void setPolicy(Policy p) {
        kernel.setSchedulingPolicy(p);
        policyLabel.set(p.name().replace("_", " "));
    }

    /**
     * Changes the Round-Robin time quantum.
     *
     * @param q quantum in ticks (≥ 1)
     */
    public void setQuantum(int q) {
        kernel.setRoundRobinQuantum(q);
    }

    // ── KernelEventListener ───────────────────────────────────────────────────

    /**
     * Invoked by the Kernel after each clock tick.
     * Refreshes all UI observables on the JavaFX Application Thread.
     *
     * @param tick    completed tick number
     * @param running the PCB that was running (null if CPU was idle)
     */
    @Override
    public void onTickCompleted(long tick, PCB running) {
        Platform.runLater(() -> {
            currentTick.set(tick);
            refreshCpu(running);
            refreshQueues();
            refreshMemory();
            refreshDisk();
            refreshStats();
        });
    }

    /**
     * Invoked when a process transitions between states.
     * Appends a state-change entry to the event log.
     *
     * @param process  the process that changed state
     * @param oldState previous state
     * @param newState new state
     */
    @Override
    public void onProcessStateChanged(PCB process, ProcessState oldState,
                                       ProcessState newState) {
        Platform.runLater(() ->
            log(String.format("[EST] %s  %s → %s",
                process.getName(),
                oldState.getDisplayName(),
                newState.getDisplayName()))
        );
    }

    /**
     * Invoked when INT 10H screen output is produced.
     * Appends the text to the event log.
     *
     * @param text text written to the simulated screen
     */
    @Override
    public void onScreenOutput(String text) {
        Platform.runLater(() -> log("[OUT] " + text));
    }

    /**
     * Invoked when a process executes INT 09H (keyboard input).
     * Pauses auto-run and enables the keyboard input row in the View.
     *
     * @param process the blocked process awaiting keyboard input
     */
    @Override
    public void onKeyboardInputRequired(PCB process) {
        Platform.runLater(() -> {
            log("[KB]  Proceso " + process.getName()
                + " espera entrada de teclado (0-255)");
            // Remember whether to resume auto after the user provides input
            kbInterruptedAuto = autoTimeline.getStatus() == Animation.Status.RUNNING;
            waitingKeyboard.set(true);
            stopAuto();
        });
    }

    /**
     * Invoked when a process terminates normally (INT 20H).
     * Logs completion details and stops single-process mode if active.
     *
     * @param process the terminated process
     */
    @Override
    public void onProcessFinished(PCB process) {
        Platform.runLater(() -> {
            log(String.format("[FIN] %s (PID=%d) terminado. Turnaround=%d Espera=%d",
                process.getName(), process.getPid(),
                process.getTurnaroundTime(), process.getWaitingTime()));
            // Single-process mode: stop as soon as ONE new process finishes
            if (singleProcRunning.get()) {
                stopSingleProcess();
            }
        });
    }

    /**
     * Invoked when all processes have terminated.
     * Sets the finished flag and stops auto-run.
     *
     * @param stats the final statistics snapshot
     */
    @Override
    public void onAllProcessesFinished(StatisticsManager stats) {
        Platform.runLater(() -> {
            log("[SIM] ══ Simulacion completada ══");
            finished.set(true);
            stopAuto();
            refreshStats();
        });
    }

    /**
     * Invoked when an instruction execution error occurs.
     * Logs the error message.
     *
     * @param process the process that caused the error
     * @param message human-readable error description
     */
    @Override
    public void onExecutionError(PCB process, String message) {
        Platform.runLater(() ->
            log("[ERR] " + process.getName() + ": " + message)
        );
    }

    // ── Internal refresh helpers ──────────────────────────────────────────────

    /**
     * Refreshes the CPU panel observables from the currently running process.
     *
     * @param running currently running PCB, or {@code null} if the CPU is idle
     */
    private void refreshCpu(PCB running) {
        if (running == null) {
            cpuStatus.set("IDLE");
            cpuProcess.set("—");
            clearRegisters();
        } else {
            cpuStatus.set("CORRIENDO");
            cpuProcess.set(running.getName() + "  (PID=" + running.getPid() + ")");
            RegisterSet r = running.getRegisters();
            regPC.set(String.valueOf(r.getPc()));
            regIR.set(String.valueOf(r.getIr()));
            regAC.set(String.valueOf(r.getAc()));
            regAX.set(String.valueOf(r.getAx()));
            regBX.set(String.valueOf(r.getBx()));
            regCX.set(String.valueOf(r.getCx()));
            regDX.set(String.valueOf(r.getDx()));
            regAH.set(String.valueOf(r.getAh()));
            regAL.set(String.valueOf(r.getAl()));
            stackInfo.set("depth=" + r.getStackDepth() + "/" + RegisterSet.STACK_SIZE);
            cpuBurstInfo.set("burst=" + running.getBurstTime()
                + "  rem=" + running.getRemainingTime()
                + "  wait=" + running.getWaitingTime());
        }
    }

    /** Resets all CPU register display properties to zero/dash. */
    private void clearRegisters() {
        regPC.set("0"); regIR.set("0"); regAC.set("0");
        regAX.set("0"); regBX.set("0"); regCX.set("0");
        regDX.set("0"); regAH.set("0"); regAL.set("0");
        stackInfo.set("depth=0/5");
        cpuBurstInfo.set("burst=0  rem=0  wait=0");
    }

    /** Rebuilds all four queue observable lists from the current kernel state. */
    private void refreshQueues() {
        fillQueueRows(newQueueRows,       new ArrayList<>(kernel.getQueueManager().getNewQueue()));
        fillQueueRows(readyQueueRows,     kernel.getQueueManager().getReadyQueue());
        fillQueueRows(blockedQueueRows,   kernel.getQueueManager().getBlockedQueue());
        fillQueueRows(terminatedQueueRows, kernel.getQueueManager().getTerminatedQueue());
    }

    /**
     * Replaces the contents of a queue-row observable list with rows built
     * from the given PCB source list.
     *
     * @param list   destination observable list
     * @param source source PCB list from the kernel
     */
    private void fillQueueRows(ObservableList<ProcessRow> list, List<PCB> source) {
        list.clear();
        for (PCB p : source) list.add(new ProcessRow(p));
    }

    /** Rebuilds the disk-row observable list from the current disk state. */
    void refreshDisk() {
        diskRows.clear();
        Disk disk         = kernel.getDisk();
        String[] cells    = disk.getCells();
        int indexSize     = Disk.INDEX_SIZE;

        // Build reverse lookup: cell address → filename
        Map<Integer, String> addrToFile = new HashMap<>();
        for (Map.Entry<String, DiskFile> entry : disk.getDirectory().entrySet()) {
            int start = entry.getValue().getStartAddress();
            for (int i = start; i < cells.length; i++) {
                if (cells[i] != null && !cells[i].isEmpty()) {
                    addrToFile.put(i, entry.getKey());
                } else {
                    break;
                }
            }
        }

        int maxOccupied = indexSize - 1;
        for (int i = cells.length - 1; i >= indexSize; i--) {
            if (cells[i] != null && !cells[i].isEmpty()) {
                maxOccupied = i;
                break;
            }
        }

        for (int i = 0; i <= maxOccupied; i++) {
            String val  = (cells[i] == null || cells[i].isEmpty()) ? "—" : cells[i];
            String zone = (i < indexSize) ? "IDX"
                        : addrToFile.containsKey(i) ? addrToFile.get(i)
                        : "LIBRE";
            diskRows.add(new DiskRow(i, zone, val));
        }

        // Rebuild disk file index rows
        diskFileRows.clear();
        for (Map.Entry<String, DiskFile> e : disk.getDirectory().entrySet()) {
            diskFileRows.add(new DiskFileRow(
                e.getKey(),
                e.getValue().getStartAddress(),
                e.getValue().getSize(),
                e.getValue().getContent()));
        }
    }

    /** Rebuilds the memory-row observable list from the current RAM state.
     *  Display order: IDX entries first, then BCP/OS area, then user space. */
    void refreshMemory() {
        memoryRows.clear();
        String[] cells = kernel.getRam().getCells();
        int osReserved     = RAM.OS_RESERVED;
        int fileIndexBase  = RAM.FILE_INDEX_BASE;

        // Find the highest occupied address in user space
        int maxOccupied = osReserved - 1;
        for (int i = cells.length - 1; i >= osReserved; i--) {
            if (cells[i] != null && !cells[i].isEmpty()) {
                maxOccupied = i;
                break;
            }
        }

        // 1. IDX area first (FILE_INDEX_BASE … OS_RESERVED-1)
        for (int i = fileIndexBase; i < osReserved; i++) {
            String val = (cells[i] == null || cells[i].isEmpty()) ? "—" : cells[i];
            memoryRows.add(new MemoryRow(i, "IDX", val));
        }

        // 2. BCP/OS area (0 … FILE_INDEX_BASE-1)
        for (int i = 0; i < fileIndexBase; i++) {
            String val = (cells[i] == null || cells[i].isEmpty()) ? "—" : cells[i];
            memoryRows.add(new MemoryRow(i, "OS", val));
        }

        // 3. User space (OS_RESERVED … maxOccupied)
        for (int i = osReserved; i <= maxOccupied; i++) {
            String val = (cells[i] == null || cells[i].isEmpty()) ? "—" : cells[i];
            memoryRows.add(new MemoryRow(i, ownerLabel(i), val));
        }
    }

    /**
     * Returns the label of the process that owns the given RAM address.
     *
     * @param address physical memory address
     * @return e.g. {@code "P3"} if owned by PID 3, or {@code "—"} if unallocated
     */
    private String ownerLabel(int address) {
        for (PCB p : kernel.getQueueManager().getAll()) {
            int base = p.getMemoryBase();
            int limit = p.getMemoryLimit();
            if (base >= 0 && address >= base && address <= limit) {
                return "P" + p.getPid();
            }
        }
        return "—";
    }

    /** Refreshes all statistics property strings from the current kernel statistics. */
    private void refreshStats() {
        StatisticsManager s = kernel.getStatisticsManager();
        statCpuUtil.set(String.format("%.1f%%", s.cpuUtilization()));
        statThroughput.set(String.format("%.4f", s.throughput()));
        statAvgWait.set(String.format("%.2f", s.avgWaitingTime()));
        statAvgTurn.set(String.format("%.2f", s.avgTurnaroundTime()));
        statAvgResp.set(String.format("%.2f", s.avgResponseTime()));
        statCtxSw.set(String.valueOf(s.getContextSwitches()));
        statTotal.set(String.valueOf(s.getTotalTicks()));
    }

    /**
     * Appends a message to the event log, capping its size at 500 entries.
     *
     * @param msg the message to append
     */
    private void log(String msg) {
        // Keep at most 500 log lines
        if (eventLog.size() >= 500) eventLog.remove(0);
        eventLog.add(msg);
    }

    // ── Accessors (called by View to bind) ────────────────────────────────────

    public LongProperty    currentTickProperty()  { return currentTick; }
    public StringProperty  policyLabelProperty()  { return policyLabel; }
    public BooleanProperty autoRunningProperty()  { return autoRunning; }
    public BooleanProperty singleProcRunningProperty() { return singleProcRunning; }
    public BooleanProperty finishedProperty()     { return finished; }
    public BooleanProperty waitingKeyboardProperty() { return waitingKeyboard; }
    public ObservableList<String> getEventLog()   { return eventLog; }

    public StringProperty cpuStatusProperty()     { return cpuStatus; }
    public StringProperty cpuProcessProperty()    { return cpuProcess; }
    public StringProperty regPCProperty()         { return regPC; }
    public StringProperty regIRProperty()         { return regIR; }
    public StringProperty regACProperty()         { return regAC; }
    public StringProperty regAXProperty()         { return regAX; }
    public StringProperty regBXProperty()         { return regBX; }
    public StringProperty regCXProperty()         { return regCX; }
    public StringProperty regDXProperty()         { return regDX; }
    public StringProperty regAHProperty()         { return regAH; }
    public StringProperty regALProperty()         { return regAL; }
    public StringProperty stackInfoProperty()     { return stackInfo; }
    public StringProperty cpuBurstInfoProperty()  { return cpuBurstInfo; }

    public ObservableList<ProcessRow>  getNewQueueRows()        { return newQueueRows; }
    public ObservableList<ProcessRow>  getReadyQueueRows()      { return readyQueueRows; }
    public ObservableList<ProcessRow>  getBlockedQueueRows()    { return blockedQueueRows; }
    public ObservableList<ProcessRow>  getTerminatedQueueRows() { return terminatedQueueRows; }

    public ObservableList<MemoryRow>    getMemoryRows()          { return memoryRows; }
    public ObservableList<DiskRow>      getDiskRows()            { return diskRows; }
    public ObservableList<DiskFileRow>  getDiskFileRows()        { return diskFileRows; }

    public StringProperty statCpuUtilProperty()    { return statCpuUtil; }
    public StringProperty statThroughputProperty() { return statThroughput; }
    public StringProperty statAvgWaitProperty()    { return statAvgWait; }
    public StringProperty statAvgTurnProperty()    { return statAvgTurn; }
    public StringProperty statAvgRespProperty()    { return statAvgResp; }
    public StringProperty statCtxSwProperty()      { return statCtxSw; }
    public StringProperty statTotalProperty()      { return statTotal; }

    public Kernel getKernel()                      { return kernel; }

    // ── Data transfer objects for TableView ───────────────────────────────────

    /**
     * Immutable snapshot of a single process for display in a JavaFX {@link javafx.scene.control.TableView}.
     * Constructed from a live {@link PCB} once per refresh; contains all fields needed by every queue table.
     */
    public static final class ProcessRow {
        private static final java.time.format.DateTimeFormatter HHMM =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm");

        private final int    pid;
        private final String name;
        private final String state;
        private final int    priority;
        private final int    burst;
        private final int    remaining;
        private final int    waiting;
        private final int    memory;
        private final int    serviceTime;
        private final int    turnaround;
        private final String turnaroundRatio;  // formatted "x.xx" or "—"
        private final int    arrivalTime;
        private final int    responseTime;
        /** Wall-clock start time formatted as HH:mm, or "—" if not started yet. */
        private final String startTimeHHmm;
        /** Wall-clock end time formatted as HH:mm, or "—" if not finished yet. */
        private final String endTimeHHmm;
        /** Real execution duration in seconds, or -1 if not finished yet. */
        private final double durationSec;

        /**
         * Builds a ProcessRow from the current state of a PCB.
         *
         * @param p the PCB to snapshot
         */
        public ProcessRow(PCB p) {
            this.pid             = p.getPid();
            this.name            = p.getName();
            this.state           = p.getState().getDisplayName();
            this.priority        = p.getPriority();
            this.burst           = p.getBurstTime();
            this.remaining       = p.getRemainingTime();
            this.waiting         = p.getWaitingTime();
            this.memory          = p.getMemoryRequired();
            this.serviceTime     = p.getServiceTime();
            this.turnaround      = p.getTurnaroundTime();
            this.turnaroundRatio = p.getServiceTime() > 0
                ? String.format("%.2f", p.getTurnaroundRatio()) : "—";
            this.arrivalTime     = p.getArrivalTime();
            this.responseTime    = p.getResponseTime();
            this.startTimeHHmm  = p.getStartTime()  != null ? p.getStartTime().format(HHMM)  : "—";
            this.endTimeHHmm    = p.getEndTime()    != null ? p.getEndTime().format(HHMM)    : "—";
            this.durationSec    = (p.getWallStartMillis() > 0 && p.getWallEndMillis() > 0)
                                  ? (p.getWallEndMillis() - p.getWallStartMillis()) / 1000.0 : -1.0;
        }

        public int    getPid()             { return pid; }
        public String getName()            { return name; }
        public String getState()           { return state; }
        public int    getPriority()        { return priority; }
        public int    getBurst()           { return burst; }
        public int    getRemaining()       { return remaining; }
        public int    getWaiting()         { return waiting; }
        public int    getMemory()          { return memory; }
        public int    getServiceTime()     { return serviceTime; }
        public int    getTurnaround()      { return turnaround; }
        public String getTurnaroundRatio() { return turnaroundRatio; }
        public int    getArrivalTime()     { return arrivalTime; }
        public int    getResponseTime()    { return responseTime; }
        public String getStartTimeHHmm()   { return startTimeHHmm; }
        public String getEndTimeHHmm()     { return endTimeHHmm; }
        public double getDurationSec()     { return durationSec; }
        public String getDurationSecStr()  { return durationSec >= 0 ? String.format("%.2f s", durationSec) : "\u2014"; }
    }

    /**
     * Immutable snapshot of a single disk cell for display in the disk-map table.
     */
    public static final class DiskRow {
        private final int    address;
        private final String zone;
        private final String value;

        public DiskRow(int address, String zone, String value) {
            this.address = address;
            this.zone    = zone;
            this.value   = value;
        }

        public int    getAddress() { return address; }
        public String getZone()    { return zone; }
        public String getValue()   { return value; }
    }

    /**
     * Immutable snapshot of a single RAM cell for display in the memory-map table.
     */
    public static final class MemoryRow {
        private final int    address;
        private final String zone;
        private final String value;

        public MemoryRow(int address, String zone, String value) {
            this.address = address;
            this.zone    = zone;
            this.value   = value;
        }

        public int    getAddress() { return address; }
        public String getZone()    { return zone; }
        public String getValue()   { return value; }
    }

    /**
     * Immutable snapshot of a file stored on the simulated disk.
     * Used to populate the disk file-index summary table.
     */
    public static final class DiskFileRow {
        private final String name;
        private final int    startAddress;
        private final int    size;
        private final String content;

        public DiskFileRow(String name, int startAddress, int size, String content) {
            this.name         = name;
            this.startAddress = startAddress;
            this.size         = size;
            this.content      = content == null ? "" : content;
        }

        public String getName()         { return name; }
        public int    getStartAddress() { return startAddress; }
        public int    getSize()         { return size; }
        public String getContent()      { return content; }
        /** Returns a one-line preview of the file content (first instruction). */
        public String getPreview() {
            if (content.isEmpty()) return "—";
            String[] lines = content.split("\n");
            return lines.length > 1 ? lines[0] + "  [+" + (lines.length - 1) + " más]" : lines[0];
        }
        /** Returns a short summary: size and first instruction. */
        public String getSummary() {
            return size + " inst.";
        }
    }
}
