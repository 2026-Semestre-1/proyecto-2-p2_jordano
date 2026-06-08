package simuladorminipc.interrupt;

/**
 * All interrupt types the simulator can raise.
 *
 * <p>Interrupts are raised by the CPU, I/O devices, or the scheduler and are
 * queued in the {@link simuladorminipc.interrupt.InterruptManager} for
 * processing by the {@link simuladorminipc.kernel.Kernel} each tick.</p>
 */
public enum InterruptType {
    PROCESS_FINISHED,  // INT 20H – normal termination
    QUANTUM_EXPIRED,   // Round-Robin quantum exhausted
    IO_REQUEST,        // Process issued an I/O operation
    IO_FINISHED,       // I/O operation completed
    PAGE_FAULT,        // Virtual memory page not in RAM (placeholder)
    KEYBOARD_INPUT,    // INT 09H – keyboard read requested
    FILE_OPERATION,    // INT 21H – file system call
    SCREEN_OUTPUT,     // INT 10H – screen write (low-priority event)
    RUNTIME_ERROR      // Execution error (stack overflow, etc.)
}
