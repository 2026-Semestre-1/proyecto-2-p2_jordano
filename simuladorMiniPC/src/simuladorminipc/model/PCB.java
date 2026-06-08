package simuladorminipc.model;

import simuladorminipc.assembler.Instruction;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process Control Block (PCB) – the complete descriptor of a process.
 * <p>
 * Fields: process state, program counter,
 * CPU registers, scheduling information, memory limits, I/O status,
 * and accounting data.
 * </p>
 *
 * <p>Lifecycle: NEW → READY → RUNNING → (BLOCKED ↔ READY) → TERMINATED</p>
 *
 * <h3>Statistics fields</h3>
 * <ul>
 *   <li><b>arrivalTime</b>   – tick at which the process entered the system.</li>
 *   <li><b>burstTime</b>     – total CPU ticks needed (sum of instruction weights).</li>
 *   <li><b>serviceTime</b>   – CPU ticks actually consumed (set at termination).</li>
 *   <li><b>waitingTime</b>   – cumulative ticks spent in the READY queue without CPU.</li>
 *   <li><b>turnaroundTime</b>– ticks from arrival to termination.</li>
 *   <li><b>responseTime</b>  – ticks from arrival to first CPU assignment.</li>
 *   <li><b>turnaroundRatio</b>– turnaroundTime / serviceTime (normalised turnaround).</li>
 * </ul>
 *
 * @see simuladorminipc.model.ProcessState
 * @see simuladorminipc.model.RegisterSet
 */
public class PCB {

    // ── PID generator ────────────────────────────────────────────────────────
    private static final AtomicInteger pidGen = new AtomicInteger(1);

    /**
     * Resets the PID auto-increment counter back to 1.
     * Must be called on kernel reset so process IDs restart from 1.
     */
    public static void resetPidCounter() { pidGen.set(1); }

    // ── Identity ─────────────────────────────────────────────────────────────
    private final int    pid;
    private final String name;

    // ── State ─────────────────────────────────────────────────────────────────
    private ProcessState state;

    // ── Scheduling ────────────────────────────────────────────────────────────
    private final int arrivalTime;
    private int       burstTime;        // total expected CPU time (sum of weights)
    private int       remainingTime;    // burst time still to execute
    private int       executedTime;     // cycles actually executed
    private int       priority;         // lower number = higher priority
    private int       quantumRemaining; // remaining ticks in current RR quantum

    // ── Statistics ────────────────────────────────────────────────────────────
    private int    waitingTime;
    private int    turnaroundTime;
    private int    responseTime;      // -1 until the process runs for the first time
    private int    serviceTime;       // actual CPU ticks used  (= burstTime when completed)
    private double turnaroundRatio;   // turnaroundTime / serviceTime  (normalised turnaround)

    // ── Memory ───────────────────────────────────────────────────────────────
    private final int memoryRequired;
    private int       memoryBase;       // physical base address in RAM
    private int       memoryLimit;      // base + memoryRequired - 1

    // ── Registers ─────────────────────────────────────────────────────────────
    private final RegisterSet registers;

    // ── I/O ──────────────────────────────────────────────────────────────────
    private final List<IORequest> pendingIORequests;
    private IORequest             currentIORequest;
    private final List<String>    openFiles;

    // ── Program ──────────────────────────────────────────────────────────────
    private List<Instruction> instructions;

    // ── Accounting ────────────────────────────────────────────────────────────
    private int       cpuId;           // which CPU slot executed this process
    private LocalTime startTime;       // wall-clock time of first run
    private LocalTime endTime;         // wall-clock time of termination
    private long      wallStartMillis;
    private long      wallEndMillis;

    // ── Control flags ─────────────────────────────────────────────────────────
    private boolean started;
    private boolean finished;

    // ── Linked list (BCP chain in memory) ────────────────────────────────────
    private PCB nextBCP;

    // ── Constructor ──────────────────────────────────────────────────────────

    /**
     * Creates a new PCB with a system-assigned PID.
     *
     * @param name            human-readable process name (usually the .asm filename without extension)
     * @param arrivalTime     simulation tick at which this process should be admitted
     * @param priority        scheduling priority; lower number = higher priority
     * @param memoryRequired  number of RAM words to reserve for this process
     */
    public PCB(String name, int arrivalTime, int priority, int memoryRequired) {
        this.pid             = pidGen.getAndIncrement();
        this.name            = name;
        this.arrivalTime     = arrivalTime;
        this.priority        = priority;
        this.memoryRequired  = memoryRequired;
        this.state           = ProcessState.NEW;
        this.registers       = new RegisterSet();
        this.pendingIORequests = new ArrayList<>();
        this.openFiles       = new ArrayList<>();
        this.instructions    = new ArrayList<>();
        this.responseTime    = -1;
        this.cpuId           = -1;
    }

    // ── Program loading ──────────────────────────────────────────────────────

    /**
     * Loads the instruction list into the PCB and computes the initial burst time.
     * The burst time equals the sum of all instruction weights.
     *
     * @param instructions assembled instruction list (must not be null or empty)
     */
    public void setInstructions(List<Instruction> instructions) {
        this.instructions = instructions;
        // Compute burst time as sum of instruction weights
        int total = 0;
        for (Instruction i : instructions) total += i.getWeight();
        this.burstTime     = total;
        this.remainingTime = total;
    }

    // ── Execution helpers ─────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the program counter has not yet reached the end of
     * the instruction list (i.e., there are still instructions to execute).
     *
     * @return {@code true} when more instructions remain
     */
    public boolean hasMoreInstructions() {
        return registers.getPc() < instructions.size();
    }

    /**
     * Returns the instruction currently pointed to by the program counter,
     * or {@code null} if the PC is out of bounds.
     *
     * @return current {@link simuladorminipc.assembler.Instruction}, or {@code null}
     */
    public Instruction currentInstruction() {
        int idx = registers.getPc();
        return (idx >= 0 && idx < instructions.size()) ? instructions.get(idx) : null;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int          getPid()            { return pid; }
    public String       getName()           { return name; }
    public ProcessState getState()          { return state; }
    public void         setState(ProcessState s) { this.state = s; }
    public int          getArrivalTime()    { return arrivalTime; }
    public int          getBurstTime()      { return burstTime; }
    public void         setBurstTime(int v) { burstTime = v; }
    public int          getRemainingTime()  { return remainingTime; }
    public void         setRemainingTime(int v) { remainingTime = v; }
    public void         decrementRemainingTime() { if (remainingTime > 0) remainingTime--; }
    public int          getExecutedTime()   { return executedTime; }
    public void         incrementExecutedTime() { executedTime++; }
    public int          getPriority()       { return priority; }
    public void         setPriority(int p)  { priority = p; }
    public int          getQuantumRemaining(){ return quantumRemaining; }
    public void         setQuantumRemaining(int q) { quantumRemaining = q; }
    public void         decrementQuantumRemaining() { if (quantumRemaining > 0) quantumRemaining--; }
    public int          getWaitingTime()    { return waitingTime; }
    public void         setWaitingTime(int v){ waitingTime = v; }
    public void         incrementWaitingTime(){ waitingTime++; }
    public int          getTurnaroundTime() { return turnaroundTime; }
    public void         setTurnaroundTime(int v){ turnaroundTime = v; }
    public int          getResponseTime()   { return responseTime; }
    public void         setResponseTime(int v){ responseTime = v; }
    public int          getServiceTime()    { return serviceTime; }
    public void         setServiceTime(int v){ serviceTime = v; }
    public double       getTurnaroundRatio(){ return turnaroundRatio; }
    public void         setTurnaroundRatio(double v){ turnaroundRatio = v; }
    public int          getMemoryRequired() { return memoryRequired; }
    public int          getMemoryBase()     { return memoryBase; }
    public void         setMemoryBase(int v){ memoryBase = v; }
    public int          getMemoryLimit()    { return memoryLimit; }
    public void         setMemoryLimit(int v){ memoryLimit = v; }
    public RegisterSet  getRegisters()      { return registers; }
    public List<IORequest> getPendingIORequests() { return pendingIORequests; }
    public void         addPendingIORequest(IORequest r) { pendingIORequests.add(r); }
    public IORequest    getCurrentIORequest(){ return currentIORequest; }
    public void         setCurrentIORequest(IORequest r){ currentIORequest = r; }
    public List<String> getOpenFiles()      { return openFiles; }
    public void         addOpenFile(String f){ openFiles.add(f); }
    public boolean      removeOpenFile(String f){ return openFiles.remove(f); }
    public List<Instruction> getInstructions(){ return instructions; }
    public int          getCpuId()          { return cpuId; }
    public void         setCpuId(int id)    { cpuId = id; }
    public LocalTime    getStartTime()      { return startTime; }
    public void         setStartTime(LocalTime t){ startTime = t; }
    public LocalTime    getEndTime()        { return endTime; }
    public void         setEndTime(LocalTime t){ endTime = t; }
    public long         getWallStartMillis(){ return wallStartMillis; }
    public void         setWallStartMillis(long v){ wallStartMillis = v; }
    public long         getWallEndMillis()  { return wallEndMillis; }
    public void         setWallEndMillis(long v){ wallEndMillis = v; }
    public boolean      isStarted()         { return started; }
    public void         setStarted(boolean v){ started = v; }
    public boolean      isFinished()        { return finished; }
    public void         setFinished(boolean v){ finished = v; }
    public PCB          getNextBCP()        { return nextBCP; }
    public void         setNextBCP(PCB n)   { nextBCP = n; }

    @Override
    public String toString() {
        return String.format("PCB[pid=%d name=%s state=%s priority=%d remaining=%d]",
            pid, name, state.getDisplayName(), priority, remainingTime);
    }
}
