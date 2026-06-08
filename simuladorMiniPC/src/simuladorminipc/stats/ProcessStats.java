package simuladorminipc.stats;

import simuladorminipc.model.PCB;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Immutable snapshot of execution statistics for a single finished process.
 *
 * <p>Captured by {@link simuladorminipc.stats.StatisticsManager#onProcessFinished}
 * at the moment the process reaches TERMINATED state.  Fields mirror those
 * </p>
 *
 * <h3>Key metrics</h3>
 * <ul>
 *   <li><b>serviceTime</b>    – actual CPU ticks used (= burstTime for completed processes).</li>
 *   <li><b>waitingTime</b>    – ticks spent in READY state without the CPU.</li>
 *   <li><b>turnaroundTime</b> – ticks from arrival to termination.</li>
 *   <li><b>responseTime</b>   – ticks from arrival to first CPU execution.</li>
 *   <li><b>turnaroundRatio</b>– turnaroundTime / serviceTime (normalised turnaround).</li>
 * </ul>
 *
 * @see StatisticsManager
 */
public final class ProcessStats {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int       pid;
    private final String    name;
    private final int       priority;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final int       burstTime;
    private final int       waitingTime;
    private final int       turnaroundTime;
    private final int       responseTime;
    private final int       serviceTime;
    private final double    turnaroundRatio;
    private final long      durationMillis;

    /**
     * Takes a statistics snapshot from a PCB at the moment it terminates.
     * All fields are copied by value; subsequent changes to the PCB are not reflected.
     *
     * @param p the terminated PCB (must have been through
     *          {@link StatisticsManager#onProcessFinished(simuladorminipc.model.PCB, long)})
     */
    public ProcessStats(PCB p) {
        this.pid            = p.getPid();
        this.name           = p.getName();
        this.priority       = p.getPriority();
        this.startTime      = p.getStartTime();
        this.endTime        = p.getEndTime();
        this.burstTime      = p.getBurstTime();
        this.waitingTime    = p.getWaitingTime();
        this.turnaroundTime = p.getTurnaroundTime();
        this.responseTime   = p.getResponseTime();
        this.serviceTime    = p.getServiceTime();
        this.turnaroundRatio= p.getTurnaroundRatio();
        this.durationMillis = (p.getWallStartMillis() > 0 && p.getWallEndMillis() > 0)
                            ? p.getWallEndMillis() - p.getWallStartMillis() : 0;
    }

    /** Returns the process ID. */
    public int       getPid()            { return pid; }

    /** Returns the process name. */
    public String    getName()           { return name; }

    /** Returns the scheduling priority (lower = higher priority). */
    public int       getPriority()       { return priority; }

    /** Returns the wall-clock time at which the process first ran, or {@code null}. */
    public LocalTime getStartTime()      { return startTime; }

    /** Returns the wall-clock time at which the process terminated, or {@code null}. */
    public LocalTime getEndTime()        { return endTime; }

    /** Returns the total CPU ticks required (sum of instruction weights). */
    public int       getBurstTime()      { return burstTime; }

    /** Returns the cumulative ticks the process spent in the READY queue without CPU. */
    public int       getWaitingTime()    { return waitingTime; }

    /** Returns the turnaround time: ticks from arrival to termination. */
    public int       getTurnaroundTime() { return turnaroundTime; }

    /** Returns the response time: ticks from arrival to first CPU execution. */
    public int       getResponseTime()   { return responseTime; }

    /** Returns the actual CPU ticks consumed (equals burstTime for completed processes). */
    public int       getServiceTime()    { return serviceTime; }

    /** Returns the normalised turnaround ratio: turnaroundTime / serviceTime. */
    public double    getTurnaroundRatio(){ return turnaroundRatio; }

    /** Returns the wall-clock duration in milliseconds from first run to termination. */
    public long      getDurationMillis() { return durationMillis; }

    /** Returns the wall-clock duration in seconds. */
    public double    getDurationSeconds(){ return durationMillis / 1000.0; }

    /** Returns a formatted start-time string (HH:mm:ss), or "--" if not available. */
    public String startTimeStr() { return startTime != null ? startTime.format(FMT) : "--"; }

    /** Returns a formatted end-time string (HH:mm:ss), or "--" if not available. */
    public String endTimeStr()   { return endTime   != null ? endTime.format(FMT)   : "--"; }

    @Override
    public String toString() {
        return String.format(
            "PID=%-3d %-12s start=%s end=%s burst=%-3d wait=%-3d turnaround=%-3d resp=%-3d",
            pid, name, startTimeStr(), endTimeStr(),
            burstTime, waitingTime, turnaroundTime, responseTime);
    }
}
