package simuladorminipc.memory;

/**
 * Represents one contiguous block of physical RAM allocated to a process.
 *
 * <p>After a process terminates the block is freed via
 * {@link simuladorminipc.memory.MemoryManager#free(simuladorminipc.model.PCB)}
 * and marked as available for re-use.</p>
 *
 * @see simuladorminipc.memory.MemoryManager
 * @see simuladorminipc.memory.RAM
 */
public class MemoryBlock {

    private final int    base;      // first physical address
    private final int    size;      // number of words reserved
    private final int    pid;       // owning process id (-1 = free / OS)
    private boolean      free;

    /**
     * Creates a memory block descriptor.
     *
     * @param base first physical address of the block
     * @param size number of words (cells) reserved
     * @param pid  PID of the owning process; {@code -1} indicates OS/free
     */
    public MemoryBlock(int base, int size, int pid) {
        this.base = base;
        this.size = size;
        this.pid  = pid;
        this.free = false;
    }

    /** Returns the first physical address of this block. */
    public int     getBase()  { return base; }

    /** Returns the number of words reserved in this block. */
    public int     getSize()  { return size; }

    /**
     * Returns the last physical address of this block (inclusive).
     *
     * @return {@code base + size - 1}
     */
    public int     getLimit() { return base + size - 1; }

    /** Returns the PID of the owning process ({@code -1} if free/OS). */
    public int     getPid()   { return pid; }

    /** Returns {@code true} if this block has been freed and is available for re-use. */
    public boolean isFree()   { return free; }

    /**
     * Marks this block as free ({@code true}) or allocated ({@code false}).
     *
     * @param f {@code true} to mark free, {@code false} to mark allocated
     */
    public void    setFree(boolean f) { free = f; }

    @Override
    public String toString() {
        return "MemBlock[base=" + base + " size=" + size
             + " pid=" + pid + " free=" + free + "]";
    }
}
