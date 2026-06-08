package simuladorminipc.cpu;

/**
 * Result codes returned by {@link CPU#executeCycle()}.
 *
 * <p>The {@link simuladorminipc.kernel.Kernel} inspects this enum to decide
 * which subsystem to invoke after each CPU tick (I/O, screen output, etc.).</p>
 */
public enum CycleResult {
    /** Normal execution; no special event. */
    NORMAL,
    /** INT 20H encountered: the process requested termination. */
    PROCESS_FINISHED,
    /** INT 10H: value in DX should be printed to the screen. */
    SCREEN_OUTPUT,
    /** INT 09H: process is now blocked waiting for keyboard input. */
    KEYBOARD_INPUT,
    /** INT 21H: process issued a file-system operation. */
    FILE_OPERATION,
    /** CPU has no loaded process (idle). */
    IDLE,
    /** Runtime error during execution (e.g. stack overflow). */
    ERROR
}
