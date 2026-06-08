package simuladorminipc.scheduler;

import simuladorminipc.model.PCB;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Holds the currently active scheduling algorithm and delegates
 * process selection to it.  Supports runtime algorithm switching.
 *
 * <p>Available policies: FCFS, ROUND_ROBIN, SPN, SRT, HRRN, PRIORITY.</p>
 *
 * @see SchedulingAlgorithm
 */
public class SchedulingPolicyManager {

    public enum Policy {
        FCFS, ROUND_ROBIN, SPN, SRT, HRRN, PRIORITY
    }

    private SchedulingAlgorithm currentAlgorithm;
    private Policy               currentPolicy;
    private int                  rrQuantum;

    /**
     * Constructs a policy manager with FCFS as the default algorithm and quantum = 2.
     */
    public SchedulingPolicyManager() {
        this(Policy.FCFS, 2);
    }

    /**
     * Constructs a policy manager with the specified initial policy and RR quantum.
     *
     * @param policy    initial scheduling policy
     * @param rrQuantum Round-Robin quantum in ticks (≥ 1; ignored for non-RR policies)
     */
    public SchedulingPolicyManager(Policy policy, int rrQuantum) {
        this.rrQuantum = rrQuantum;
        setPolicy(policy);
    }

    // ── Selection ─────────────────────────────────────────────────────────────

    /**
     * Delegates process selection to the active algorithm.
     *
     * @return the next PCB to dispatch, or null if no switch should occur
     */
    public PCB selectNext(List<PCB> readyProcesses, PCB currentProcess) {
        return currentAlgorithm.selectNextProcess(readyProcesses, currentProcess);
    }

    // ── Policy switching ──────────────────────────────────────────────────────

    /**
     * Switches the active scheduling algorithm at runtime.
     * Instantiates the corresponding concrete scheduler implementation.
     *
     * @param policy the policy to activate
     */
    public void setPolicy(Policy policy) {
        this.currentPolicy = policy;
        switch (policy) {
            case FCFS:        currentAlgorithm = new FCFSScheduler();              break;
            case ROUND_ROBIN: currentAlgorithm = new RoundRobinScheduler(rrQuantum); break;
            case SPN:         currentAlgorithm = new SPNScheduler();               break;
            case SRT:         currentAlgorithm = new SRTScheduler();               break;
            case HRRN:        currentAlgorithm = new HRRNScheduler();              break;
            case PRIORITY:    currentAlgorithm = new PriorityScheduler();          break;
            default:          currentAlgorithm = new FCFSScheduler();
        }
    }

    /**
     * Changes the Round-Robin quantum and re-instantiates the RR scheduler
     * if ROUND_ROBIN is the current policy.
     *
     * @param quantum new quantum value in ticks (≥ 1)
     */
    public void setRoundRobinQuantum(int quantum) {
        this.rrQuantum = quantum;
        if (currentPolicy == Policy.ROUND_ROBIN)
            currentAlgorithm = new RoundRobinScheduler(quantum);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** Returns the currently active scheduler algorithm instance. */
    public SchedulingAlgorithm getCurrentAlgorithm() { return currentAlgorithm; }

    /** Returns the currently active policy enum value. */
    public Policy               getCurrentPolicy()    { return currentPolicy; }

    /** Returns the configured Round-Robin quantum (may differ from the active scheduler if policy changed). */
    public int                  getRrQuantum()        { return rrQuantum; }

    /** Returns the human-readable name of the currently active algorithm. */
    public String               getAlgorithmName()    { return currentAlgorithm.getName(); }

    /**
     * Returns an unmodifiable list of all available scheduling policies.
     *
     * @return immutable list of {@link Policy} values
     */
    public static List<Policy>  availablePolicies() {
        return Collections.unmodifiableList(Arrays.asList(Policy.values()));
    }
}
