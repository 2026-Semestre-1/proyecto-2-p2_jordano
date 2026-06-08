package simuladorminipc.process;

import simuladorminipc.model.PCB;
import simuladorminipc.model.ProcessState;

/**
 * Centralises all legal process state transitions.
 * <p>
 * Every state change in the system must go through this class to enforce
 * the OS state-machine invariants and keep state logic in one place.
 * </p>
 *
 * <pre>
 * NEW → READY         (admitted + memory allocated)
 * READY → RUNNING     (dispatched by CPU)
 * RUNNING → READY     (preempted / quantum expired)
 * RUNNING → BLOCKED   (issued I/O or keyboard request)
 * RUNNING → TERMINATED(INT 20H or last instruction)
 * BLOCKED → READY     (I/O completed)
 * READY → SUSPENDED_READY    (swapped out while ready)
 * BLOCKED → SUSPENDED_BLOCKED(swapped out while blocked)
 * SUSPENDED_* → READY (swapped back in)
 * </pre>
 */
public class StateManager {

    private final QueueManager queueManager;

    /**
     * Constructs a state manager.
     *
     * @param queueManager the queue manager used to move processes between queues
     */
    public StateManager(QueueManager queueManager) {
        this.queueManager = queueManager;
    }

    /**
     * Transitions a process from NEW to READY after physical memory has been allocated.
     * Also moves the PCB into the ready queue.
     *
     * @param p process in NEW state
     * @throws IllegalStateException if the process is not in NEW state
     */
    public void admitToReady(PCB p) {
        guardTransition(p, ProcessState.NEW, ProcessState.READY);
        p.setState(ProcessState.READY);
        queueManager.moveToReady(p);
    }

    /**
     * Sets a process to RUNNING and removes it from all queues.
     * Called by the Dispatcher/Kernel immediately before the CPU executes.
     *
     * @param p process selected by the scheduler
     */
    public void setRunning(PCB p) {
        p.setState(ProcessState.RUNNING);
        // The running process is not in any queue
        queueManager.removeFromAll(p);
    }

    /**
     * Preempts the running process: moves it back to READY and enqueues it.
     * Used on quantum expiry (Round-Robin) or when a higher-priority process arrives.
     *
     * @param p the process to preempt
     */
    public void preemptToReady(PCB p) {
        p.setState(ProcessState.READY);
        queueManager.moveToReady(p);
    }

    /**
     * Blocks the running process on an I/O request.
     * Moves it to BLOCKED state and the blocked queue.
     *
     * @param p the process issuing the I/O request
     */
    public void blockOnIO(PCB p) {
        p.setState(ProcessState.BLOCKED);
        queueManager.moveToBlocked(p);
    }

    /**
     * Unblocks a process after its I/O request completes.
     * Moves it from BLOCKED to READY state and back to the ready queue.
     *
     * @param p the process whose I/O just finished
     */
    public void unblockToReady(PCB p) {
        p.setState(ProcessState.READY);
        queueManager.moveToReady(p);
    }

    /**
     * Terminates a process: sets its state to TERMINATED, marks it finished,
     * and moves it to the terminated queue.
     *
     * @param p the process to terminate
     */
    public void terminate(PCB p) {
        p.setState(ProcessState.TERMINATED);
        p.setFinished(true);
        queueManager.moveToTerminated(p);
    }

    /**
     * Suspends a READY process (swap out): READY → SUSPENDED_READY.
     *
     * @param p process in READY state
     * @throws IllegalStateException if the process is not in READY state
     */
    public void suspendReady(PCB p) {
        guardTransition(p, ProcessState.READY, ProcessState.SUSPENDED_READY);
        p.setState(ProcessState.SUSPENDED_READY);
        queueManager.moveToSuspended(p);
    }

    /**
     * Suspends a BLOCKED process (swap out): BLOCKED → SUSPENDED_BLOCKED.
     *
     * @param p process in BLOCKED state
     * @throws IllegalStateException if the process is not in BLOCKED state
     */
    public void suspendBlocked(PCB p) {
        guardTransition(p, ProcessState.BLOCKED, ProcessState.SUSPENDED_BLOCKED);
        p.setState(ProcessState.SUSPENDED_BLOCKED);
        queueManager.moveToSuspended(p);
    }

    /**
     * Resumes a swapped-out process into the READY state (swap in).
     * Moves it to the ready queue.
     *
     * @param p process in SUSPENDED_READY or SUSPENDED_BLOCKED state
     */
    public void resumeToReady(PCB p) {
        p.setState(ProcessState.READY);
        queueManager.moveToReady(p);
    }

    /**
     * Validates that a process is in the expected state before a transition.
     * Throws {@link IllegalStateException} with a descriptive message if not.
     *
     * @param p        the process being checked
     * @param expected the required current state
     * @param next     the intended next state (used in the error message)
     * @throws IllegalStateException if the actual state differs from expected
     */
    private static void guardTransition(PCB p, ProcessState expected, ProcessState next) {
        if (p.getState() != expected)
            throw new IllegalStateException(
                "Invalid state transition for PID " + p.getPid()
                + ": expected " + expected + " but found " + p.getState()
                + " (attempting → " + next + ")");
    }
}
