package simuladorminipc.kernel;

import simuladorminipc.model.PCB;
import simuladorminipc.model.ProcessState;
import simuladorminipc.stats.StatisticsManager;

/**
 * Observer interface that the GUI (or any consumer) implements to receive
 * real-time kernel events without coupling the Kernel to Swing.
 */
public interface KernelEventListener {

    /** Called at the end of every tick with the tick number and current runner. */
    void onTickCompleted(long tick, PCB runningProcess);

    /** Called whenever a process changes state. */
    void onProcessStateChanged(PCB process, ProcessState oldState, ProcessState newState);

    /** Called when INT 10H is executed: text to display on screen. */
    void onScreenOutput(String text);

    /**
     * Called when INT 09H is executed: the process is now BLOCKED and the UI
     * should prompt the user for a numeric value (0–255).
     * After collecting the value the UI must call
     * {@code kernel.provideKeyboardInput(process, value)}.
     */
    void onKeyboardInputRequired(PCB process);

    /** Called when a process has terminated. */
    void onProcessFinished(PCB process);

    /** Called when all processes have finished. */
    void onAllProcessesFinished(StatisticsManager stats);

    /** Called when a runtime error occurs during instruction execution. */
    void onExecutionError(PCB process, String message);
}
