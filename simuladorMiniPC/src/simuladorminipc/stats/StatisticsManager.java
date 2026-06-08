package simuladorminipc.stats;

import simuladorminipc.cpu.CPU;
import simuladorminipc.model.PCB;
import simuladorminipc.model.ProcessState;
import simuladorminipc.process.QueueManager;

import java.time.LocalTime;
import java.util.*;

/**
 * Collects and computes OS-level performance statistics.
 * <p>
 * Updated by the Kernel each tick and at process state transitions.
 * Exposes both per-process snapshots ({@link ProcessStats}) and
 * system-wide aggregates (CPU utilisation, throughput, average times).
 * </p>
 *
 * @see ProcessStats
 * @see simuladorminipc.kernel.Kernel
 */
public class StatisticsManager {

    private final QueueManager   queueManager;
    private final CPU            cpu;

    private long   totalTicks;
    private long   idleTicks;
    private long   contextSwitches;

    private final List<ProcessStats> completedStats = new ArrayList<>();

    /**
     * Constructs a statistics manager.
     *
     * @param queueManager the system queue manager (needed to iterate the ready queue)
     * @param cpu          the CPU instance (needed to track idle cycles)
     */
    public StatisticsManager(QueueManager queueManager, CPU cpu) {
        this.queueManager = queueManager;
        this.cpu          = cpu;
    }

    // ── Per-tick update ───────────────────────────────────────────────────────

    /**
     * Called once per tick.
     * Increments waiting time for every process in the ready queue,
     * and updates idle/busy counters.
     *
     * @param currentTick current system clock value
     */
    public void onTick(long currentTick) {
        totalTicks++;
        if (cpu.isIdle()) idleTicks++;

        // Increment waiting time for all ready processes
        for (PCB p : queueManager.getReadyQueue()) {
            p.incrementWaitingTime();
        }
    }

    // ── Process lifecycle events ───────────────────────────────────────────────

    /**
     * Called when a process first receives the CPU.
     * Sets the response time (ticks from arrival to first execution) once.
     *
     * @param p    the process that started running for the first time
     * @param tick current system tick
     */
    public void onProcessFirstRun(PCB p, long tick) {
        if (p.getResponseTime() < 0) {
            p.setResponseTime((int)(tick - p.getArrivalTime()));
            p.setStarted(true);
            p.setStartTime(LocalTime.now());
            p.setWallStartMillis(System.currentTimeMillis());
        }
    }

    /**
     * Called when a process terminates.
     * Captures final turnaround time, service time, and normalised turnaround ratio.
     *
     * @param p    the terminated process
     * @param tick the tick at which termination occurred
     */
    public void onProcessFinished(PCB p, long tick) {
        p.setEndTime(LocalTime.now());
        p.setWallEndMillis(System.currentTimeMillis());
        int turnaround = (int)(tick - p.getArrivalTime());
        p.setTurnaroundTime(turnaround);
        // Service time = total CPU ticks the process needed (= burstTime for completed processes)
        int service = p.getBurstTime();
        p.setServiceTime(service);
        // Normalised turnaround: how many times the service time the process spent in the system
        double ratio = service > 0 ? turnaround / (double) service : 0.0;
        p.setTurnaroundRatio(ratio);
        completedStats.add(new ProcessStats(p));
    }

    /**
     * Sets the number of context switches recorded by the
     * {@link simuladorminipc.cpu.Dispatcher}.
     *
     * @param count cumulative context switch count
     */
    public void setContextSwitches(long count) { contextSwitches = count; }

    // ── Computed metrics ──────────────────────────────────────────────────────

    /**
     * Returns the CPU utilisation percentage over all ticks so far.
     *
     * @return value in the range [0.0, 100.0]
     */
    public double cpuUtilization() {
        return totalTicks == 0 ? 0.0 : (totalTicks - idleTicks) / (double) totalTicks * 100.0;
    }

    /**
     * Returns the system throughput: number of completed processes per tick.
     *
     * @return throughput value (≥ 0.0)
     */
    public double throughput() {
        return totalTicks == 0 ? 0.0 : completedStats.size() / (double) totalTicks;
    }

    /**
     * Returns the average waiting time across all completed processes.
     *
     * @return average ticks spent waiting, or 0.0 if no processes have finished
     */
    public double avgWaitingTime() {
        return completedStats.isEmpty() ? 0 :
            completedStats.stream().mapToInt(ProcessStats::getWaitingTime).average().orElse(0);
    }

    /**
     * Returns the average turnaround time across all completed processes.
     *
     * @return average turnaround in ticks, or 0.0 if no processes have finished
     */
    public double avgTurnaroundTime() {
        return completedStats.isEmpty() ? 0 :
            completedStats.stream().mapToInt(ProcessStats::getTurnaroundTime).average().orElse(0);
    }

    /**
     * Returns the average response time across all completed processes.
     *
     * @return average response time in ticks, or 0.0 if no processes have finished
     */
    public double avgResponseTime() {
        return completedStats.isEmpty() ? 0 :
            completedStats.stream().mapToInt(ProcessStats::getResponseTime).average().orElse(0);
    }

    // ── Report ────────────────────────────────────────────────────────────────

    /**
     * Generates a formatted text report with all system statistics and a per-process table.
     *
     * @return multi-line report string
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════════\n");
        sb.append("                   ESTADÍSTICAS FINALES                   \n");
        sb.append("═══════════════════════════════════════════════════════════\n");
        sb.append(String.format("Ticks totales       : %d%n",  totalTicks));
        sb.append(String.format("Uso de CPU          : %.1f%%%n", cpuUtilization()));
        sb.append(String.format("Cambios de contexto : %d%n",  contextSwitches));
        sb.append(String.format("Rendimiento         : %.4f proc/tick%n", throughput()));
        sb.append(String.format("Espera promedio     : %.2f ticks%n", avgWaitingTime()));
        sb.append(String.format("Retorno promedio    : %.2f ticks%n", avgTurnaroundTime()));
        sb.append(String.format("Respuesta promedio  : %.2f ticks%n", avgResponseTime()));
        sb.append("\nDetalle por proceso:\n");
        sb.append(String.format("%-4s %-12s %-8s %-8s %-6s %-5s %-8s %-5s%n",
            "PID","Nombre","Inicio","Fin","Burst","Esp.","Retorno","Resp."));
        sb.append("─".repeat(62)).append('\n');
        for (ProcessStats s : completedStats) {
            sb.append(String.format("%-4d %-12s %-8s %-8s %-6d %-5d %-8d %-5d%n",
                s.getPid(), s.getName(),
                s.startTimeStr(), s.endTimeStr(),
                s.getBurstTime(), s.getWaitingTime(),
                s.getTurnaroundTime(), s.getResponseTime()));
        }
        sb.append("═══════════════════════════════════════════════════════════\n");
        return sb.toString();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** Returns the total number of ticks elapsed since the simulation started. */
    public long               getTotalTicks()    { return totalTicks; }

    /** Returns the number of ticks in which the CPU had no process to run. */
    public long               getIdleTicks()     { return idleTicks; }

    /** Returns the cumulative number of context switches performed. */
    public long               getContextSwitches(){ return contextSwitches; }

    /**
     * Returns an unmodifiable list of statistics snapshots for all
     * completed processes in termination order.
     *
     * @return read-only list of {@link ProcessStats}
     */
    public List<ProcessStats> getCompletedStats() { return Collections.unmodifiableList(completedStats); }
}
