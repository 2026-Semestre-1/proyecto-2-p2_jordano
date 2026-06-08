package simuladorminipc.memory;

/**
 * Models the physical RAM of the simulated computer.
 * <p>
 * Each cell stores a {@code String} (instruction text or data value).
 * Size is configurable; default 512.
 * Addresses 0 … OS_RESERVED-1 are reserved for OS data structures (BCPs, index).
 * </p>
 *
 * <p>Address 0 is the lowest valid address; addresses ≥ {@link #getSize()} are
 * rejected with {@link IndexOutOfBoundsException}.</p>
 *
 * @see MemoryManager
 * @see MemoryBlock
 */
public class RAM {

    /** Number of cells reserved for OS structures (BCPs). */
    public static final int BCP_AREA_SIZE  = 20;

    /** Base address of the file index within the OS area. */
    public static final int FILE_INDEX_BASE = 20;

    /** Number of cells in the file index (one entry per loaded file). */
    public static final int FILE_INDEX_SIZE = 10;

    /** Total cells reserved for OS structures (BCPs + file index). */
    public static final int OS_RESERVED = BCP_AREA_SIZE + FILE_INDEX_SIZE;

    private final int      size;
    private final String[] cells;

    /** Creates a 512-cell RAM (the default simulated size). */
    public RAM() { this(512); }

    /**
     * Creates a RAM with a custom number of cells.
     *
     * @param size total number of addressable cells
     * @throws IllegalArgumentException if {@code size} ≤ {@link #OS_RESERVED}
     */
    public RAM(int size) {
        if (size <= OS_RESERVED)
            throw new IllegalArgumentException(
                "RAM size must be greater than OS_RESERVED (" + OS_RESERVED + ").");
        this.size  = size;
        this.cells = new String[size];
        clear();
    }

    // ── Access ───────────────────────────────────────────────────────────────

    /**
     * Writes a string value to a RAM cell.
     *
     * @param address target cell address (0 ≤ address &lt; size)
     * @param value   string to store (may be empty, not null)
     * @throws IndexOutOfBoundsException if address is out of range
     */
    public void write(int address, String value) {
        checkBounds(address);
        cells[address] = value;
    }

    /**
     * Reads the string value stored in a RAM cell.
     *
     * @param address source cell address
     * @return stored string (may be empty, never null after {@link #clear()})
     * @throws IndexOutOfBoundsException if address is out of range
     */
    public String read(int address) {
        checkBounds(address);
        return cells[address];
    }

    /** Sets every cell to an empty string. */
    public void clear() {
        for (int i = 0; i < size; i++) cells[i] = "";
    }

    /**
     * Sets cells in the range [{@code from}, {@code to}] to empty strings.
     *
     * @param from first cell to clear (inclusive)
     * @param to   last cell to clear (inclusive)
     */
    public void clearRange(int from, int to) {
        for (int i = from; i <= to && i < size; i++) cells[i] = "";
    }

    // ── Metadata ─────────────────────────────────────────────────────────────

    /** Returns the total number of cells in this RAM. */
    public int      getSize()     { return size; }

    /** Returns a direct reference to the internal cell array (use read-only). */
    public String[] getCells()    { return cells; }

    /** Returns the first user-space address (= {@link #OS_RESERVED}). */
    public int      userStart()   { return OS_RESERVED; }

    /** Returns the last valid address ({@code size - 1}). */
    public int      userEnd()     { return size - 1; }

    private void checkBounds(int address) {
        if (address < 0 || address >= size)
            throw new IndexOutOfBoundsException(
                "Memory address " + address + " out of bounds [0," + (size-1) + "].");
    }
}
