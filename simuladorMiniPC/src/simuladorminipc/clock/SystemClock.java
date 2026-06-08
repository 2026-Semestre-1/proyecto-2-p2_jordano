package simuladorminipc.clock;

/**
 * Monotonically-increasing system clock.
 * Each {@link #tick()} call advances time by one unit (one “second”
 * in the project’s execution model).
 *
 * <p>The clock is used by the {@link simuladorminipc.kernel.Kernel} to order
 * events and by the {@link simuladorminipc.stats.StatisticsManager} to compute
 * timing metrics (arrival, turnaround, response time).</p>
 */
public class SystemClock {

    private long currentTick;

    /** Creates a new clock initialised to tick zero. */
    public SystemClock() { currentTick = 0; }

    /** Advances the clock by exactly one tick and returns the new value.
     *
     * @return the new tick value after incrementing
     */
    public long tick() { return ++currentTick; }

    /**
     * Returns the current tick without advancing the clock.
     *
     * @return current tick value (≥ 0)
     */
    public long getCurrentTick() { return currentTick; }

    /** Resets the clock back to zero. */
    public void reset() { currentTick = 0; }

    @Override
    public String toString() { return "Tick=" + currentTick; }
}
