package simuladorminipc.memory;

import simuladorminipc.model.PCB;
import simuladorminipc.storage.DiskConfig;

/**
 * Virtual memory manager for the simulated minicomputer.
 * <p>
 * Manages the mapping between a process's <em>virtual</em> address space and
 * physical RAM frames.  When a virtual page is not resident in RAM it must be
 * retrieved from secondary storage (disk / swap space) — this is why virtual
 * memory is architecturally backed by <strong>disk</strong>, not by RAM itself.
 * RAM only holds the currently <em>resident</em> pages; the rest reside in the
 * swap area on disk, managed by {@link SwapManager}.
 * </p>
 *
 * <p>In this simulator the full paging mechanism is a placeholder: all
 * translations delegate to the physical {@link MemoryManager} directly.
 * A future implementation would maintain a per-process page table, detect
 * page faults, and invoke {@link SwapManager#swapIn} / {@link SwapManager#swapOut}
 * to move pages between RAM and disk.</p>
 *
 * @see MemoryManager
 * @see SwapManager
 */
public class VirtualMemoryManager {

    /** Default virtual address space size per process (words). */
    public static final int DEFAULT_VIRTUAL_SIZE = 64;

    private final MemoryManager physicalManager;

    /**
     * Backing store for pages evicted from RAM.
     * Virtual memory overflow always goes to disk (swap), never stays in RAM.
     */
    private final SwapManager swapManager;

    /** Configurable virtual address space size per process (words). */
    private final int virtualSize;

    /**
     * Constructs a virtual memory manager backed by physical RAM and disk swap.
     *
     * @param physicalManager the physical RAM manager (resident pages)
     * @param swapManager     the disk-backed swap manager (non-resident pages)
     */
    public VirtualMemoryManager(MemoryManager physicalManager, SwapManager swapManager) {
        this.physicalManager = physicalManager;
        this.swapManager     = swapManager;
        this.virtualSize     = DiskConfig.load().virtualMemorySize;
    }

    /**
     * Constructs a virtual memory manager with no swap backing.
     * All address translations go directly to physical RAM.
     *
     * @param physicalManager the underlying physical memory manager
     * @deprecated Prefer {@link #VirtualMemoryManager(MemoryManager, SwapManager)}
     *             so that virtual-memory overflow correctly targets disk.
     */
    @Deprecated
    public VirtualMemoryManager(MemoryManager physicalManager) {
        this(physicalManager, new SwapManager());
    }

    /**
     * Translates a virtual address to a physical address.
     * Currently returns {@code virtualAddress + process.getMemoryBase()}.
     *
     * @param process        the process whose address space is being translated
     * @param virtualAddress virtual address within the process’s address space
     * @return corresponding physical RAM address
     */
    public int translate(PCB process, int virtualAddress) {
        return process.getMemoryBase() + virtualAddress;
    }

    /**
     * Checks whether the virtual address is within the process’s allocated region.
     *
     * @param process        the owning process
     * @param virtualAddress address to validate
     * @return {@code true} if the corresponding physical address lies within
     *         {@code [memoryBase, memoryLimit]}
     */
    public boolean isValid(PCB process, int virtualAddress) {
        if (virtualAddress < 0 || virtualAddress >= virtualSize) return false;
        int physical = translate(process, virtualAddress);
        return physical >= process.getMemoryBase()
            && physical <= process.getMemoryLimit();
    }

    /** Returns the configured virtual address space size (words per process). */
    public int getVirtualSize() { return virtualSize; }

    /** Returns the underlying physical memory manager. */
    public MemoryManager getPhysicalManager() { return physicalManager; }

    /**
     * Returns the disk-backed swap manager used to hold non-resident pages.
     * Virtual memory overflow always targets disk, not RAM.
     */
    public SwapManager getSwapManager() { return swapManager; }
}
