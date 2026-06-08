package simuladorminipc.memory;

import simuladorminipc.assembler.Instruction;
import simuladorminipc.model.PCB;

import java.util.ArrayList;
import java.util.List;

/**
 * Allocates and frees contiguous RAM regions for processes using a
 * first-fit strategy, and loads program instructions into RAM cells.
 *
 * <p>The first {@link RAM#OS_RESERVED} cells are reserved for OS data
 * structures (BCP summaries).  User programs are loaded starting at
 * {@link RAM#userStart()}.</p>
 *
 * @see RAM
 * @see MemoryBlock
 * @see simuladorminipc.model.PCB
 */
public class MemoryManager {

    private final RAM               ram;
    private final List<MemoryBlock> allocated;
    private int                     nextFreeAddress;

    /**
     * Constructs a memory manager backed by the given RAM.
     *
     * @param ram the physical RAM instance to manage
     */
    public MemoryManager(RAM ram) {
        this.ram             = ram;
        this.allocated       = new ArrayList<>();
        this.nextFreeAddress = ram.userStart();
    }

    // ── Allocation ────────────────────────────────────────────────────────────

    /**
     * Attempts to allocate memory for {@code process} using first-fit.
     *
     * @return true if allocation succeeded; false if insufficient space.
     */
    public boolean allocate(PCB process) {
        int required = process.getMemoryRequired();
        int base     = findFreeBlock(required);
        if (base == -1) return false;

        // Write instructions to RAM cells
        List<Instruction> instrs = process.getInstructions();
        for (int i = 0; i < instrs.size() && (base + i) < ram.getSize(); i++) {
            ram.write(base + i, instrs.get(i).getOriginal());
        }

        // Store BCP header in OS area (first 20 addresses)
        storeBcpInOsArea(process, base);

        process.setMemoryBase(base);
        process.setMemoryLimit(base + required - 1);
        process.getRegisters().setPc(0);  // PC is instruction list index, not address

        MemoryBlock block = new MemoryBlock(base, required, process.getPid());
        allocated.add(block);

        // Advance next-free pointer (simple bump allocator)
        updateNextFree();
        return true;
    }

    /** Frees the RAM region occupied by {@code process}. */
    public void free(PCB process) {
        allocated.removeIf(b -> {
            if (b.getPid() == process.getPid()) {
                ram.clearRange(b.getBase(), b.getLimit());
                b.setFree(true);
                return true;
            }
            return false;
        });
        // Clear OS area entry
        clearBcpFromOsArea(process);
        updateNextFree();
    }

    /**
     * Returns the number of user-space words not yet allocated to any process.
     *
     * @return free word count (≥ 0)
     */
    public int freeWords() {
        int used = 0;
        for (MemoryBlock b : allocated) used += b.getSize();
        return ram.userEnd() - ram.userStart() + 1 - used;
    }

    /** Returns the list of currently allocated memory blocks. */
    public List<MemoryBlock> getAllocated() { return allocated; }

    /** Returns the RAM instance this manager operates on. */
    public RAM               getRam()       { return ram; }

    /**
     * Clears the allocator’s internal state (block list and next-free pointer).
     * The RAM cells themselves are not cleared here – call {@link RAM#clear()} separately.
     * Must be called during kernel reset before new processes are loaded.
     */
    public void reset() {
        allocated.clear();
        nextFreeAddress = ram.userStart();
    }

    // ── BCP in OS area (addresses 0–19) ──────────────────────────────────────
    //
    // Layout: 4 cells per process, up to 5 processes = 20 cells exactly.
    //   Slot 0 → cells  0– 3
    //   Slot 1 → cells  4– 7
    //   Slot 2 → cells  8–11
    //   Slot 3 → cells 12–15
    //   Slot 4 → cells 16–19
    //
    // Cells 20–29 are used for the file index (see RAM.FILE_INDEX_BASE).
    //
    //   cell+0: BCP PID=x NOM=name PRIO=p
    //   cell+1: BASE=n LIM=m BURST=b REM=r
    //   cell+2: WAIT=w TURN=t SERV=s T/S=r.rr
    //   cell+3: PC=n IR=n AC=n AX=n EST=state

    private static final int BCP_CELLS_PER_PROC = 4;
    /** Maximum number of BCP slots in the OS area (fixed at 5, cells 0–19). */
    private static final int BCP_MAX_SLOTS      = RAM.BCP_AREA_SIZE / BCP_CELLS_PER_PROC;

    private void storeBcpInOsArea(PCB process, int base) {
        int slot = findFreeOsSlot();
        if (slot < 0) return;   // OS area full (should not happen with MAX=5)
        writeBcpSlot(slot, process);
    }

    /** Refreshes the BCP entry for a process that is currently in memory. */
    public void updateBcpOsArea(PCB process) {
        int slot = findOsSlotForPid(process.getPid());
        if (slot >= 0) writeBcpSlot(slot, process);
    }

    private void writeBcpSlot(int slot, PCB process) {
        int base  = slot * BCP_CELLS_PER_PROC;
        simuladorminipc.model.RegisterSet r = process.getRegisters();
        String ratioStr = process.getServiceTime() > 0
            ? String.format("%.2f", process.getTurnaroundRatio()) : "—";
        ram.write(base,     "BCP PID=" + process.getPid()
                          + " NOM=" + process.getName()
                          + " PRIO=" + process.getPriority());
        ram.write(base + 1, "BASE=" + process.getMemoryBase()
                          + " LIM=" + process.getMemoryLimit()
                          + " BURST=" + process.getBurstTime()
                          + " REM=" + process.getRemainingTime());
        ram.write(base + 2, "WAIT=" + process.getWaitingTime()
                          + " TURN=" + process.getTurnaroundTime()
                          + " SERV=" + process.getServiceTime()
                          + " T/S=" + ratioStr);
        ram.write(base + 3, "PC=" + r.getPc()
                          + " IR=" + r.getIr()
                          + " AC=" + r.getAc()
                          + " AX=" + r.getAx()
                          + " EST=" + process.getState().getDisplayName());
    }

    private void clearBcpFromOsArea(PCB process) {
        int slot = findOsSlotForPid(process.getPid());
        if (slot < 0) return;
        int base = slot * BCP_CELLS_PER_PROC;
        for (int i = 0; i < BCP_CELLS_PER_PROC; i++) {
            ram.write(base + i, "");
        }
    }

    private int findFreeOsSlot() {
        for (int s = 0; s < BCP_MAX_SLOTS; s++) {
            String cell = ram.read(s * BCP_CELLS_PER_PROC);
            if (cell == null || cell.isEmpty()) return s;
        }
        return -1;
    }

    private int findOsSlotForPid(int pid) {
        String prefix = "BCP PID=" + pid + " ";
        for (int s = 0; s < BCP_MAX_SLOTS; s++) {
            String cell = ram.read(s * BCP_CELLS_PER_PROC);
            if (cell != null && cell.startsWith(prefix)) return s;
        }
        return -1;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** First-fit: find the first contiguous free block of {@code size} words. */
    private int findFreeBlock(int size) {
        // Build a boolean "in-use" map over user space
        int userStart = ram.userStart();
        int userEnd   = ram.userEnd();
        boolean[] used = new boolean[ram.getSize()];
        for (MemoryBlock b : allocated) {
            for (int a = b.getBase(); a <= b.getLimit() && a < used.length; a++)
                used[a] = true;
        }
        // Scan for first contiguous free region
        int count = 0, start = -1;
        for (int a = userStart; a <= userEnd; a++) {
            if (!used[a]) {
                if (count == 0) start = a;
                count++;
                if (count >= size) return start;
            } else {
                count = 0;
                start = -1;
            }
        }
        return -1;
    }

    private void updateNextFree() {
        boolean[] used = new boolean[ram.getSize()];
        for (MemoryBlock b : allocated) {
            for (int a = b.getBase(); a <= b.getLimit() && a < used.length; a++)
                used[a] = true;
        }
        nextFreeAddress = ram.userStart();
        for (int a = ram.userStart(); a <= ram.userEnd(); a++) {
            if (!used[a]) { nextFreeAddress = a; break; }
        }
    }
}
