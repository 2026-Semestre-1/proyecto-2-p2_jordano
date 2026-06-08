package simuladorminipc.interrupt;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 * Maintains the pending interrupt queue processed by the Kernel each tick.
 * Interrupts are handled FIFO.
 *
 * <p>Thread-safety note: this class is not thread-safe. All calls must
 * originate from the JavaFX Application Thread (or the simulation thread).</p>
 *
 * @see Interrupt
 * @see InterruptType
 */
public class InterruptManager {

    private final Deque<Interrupt> queue = new ArrayDeque<>();

    /** Adds a new interrupt to the tail of the pending queue. */
    public void raise(Interrupt interrupt) {
        queue.addLast(interrupt);
    }

    /** Removes and returns the next pending interrupt, or null if empty. */
    public Interrupt poll() {
        return queue.pollFirst();
    }

    /** Returns (without removing) the next pending interrupt, or null if empty. */
    public Interrupt peek() {
        return queue.peekFirst();
    }

    /** Returns {@code true} if there is at least one pending interrupt. */
    public boolean hasPending()   { return !queue.isEmpty(); }
    /**
     * Returns the number of interrupts currently waiting to be processed.
     *
     * @return non-negative count
     */
    public int     pendingCount() { return queue.size(); }

    /**
     * Returns a snapshot of all pending interrupts in FIFO order (read-only).
     *
     * @return unmodifiable list of pending interrupts
     */
    public List<Interrupt> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    /** Removes all pending interrupts from the queue. */
    public void clear() { queue.clear(); }
}
