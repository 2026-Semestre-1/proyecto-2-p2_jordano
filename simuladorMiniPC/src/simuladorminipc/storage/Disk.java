package simuladorminipc.storage;

import simuladorminipc.assembler.Instruction;

import java.util.*;

/**
 * Simulates the secondary storage unit of the minicomputer.
 * <p>
 * Layout of the disk cell array:
 * <ul>
 *   <li>Cells 0 … INDEX_SIZE-1 : file index (name → start-address pairs).</li>
 *   <li>Cells INDEX_SIZE … size-1 : file data area.</li>
 * </ul>
 * Default disk size: 512 cells (configurable).
 * </p>
 */
public class Disk {

    /** Number of cells reserved for the file index at the start of disk. */
    public static final int INDEX_SIZE = 10;

    private final int      size;
    private final String[] cells;

    /** In-memory file directory keyed by filename. */
    private final Map<String, DiskFile> directory = new LinkedHashMap<>();
    private int nextDataAddress = INDEX_SIZE;

    /**
     * Creates a Disk with the default 512-cell size.
     */
    public Disk() { this(512); }

    /**
     * Creates a Disk with a custom number of cells.
     *
     * @param size total number of disk cells (> {@link #INDEX_SIZE})
     * @throws IllegalArgumentException if {@code size} ≤ {@link #INDEX_SIZE}
     */
    public Disk(int size) {
        if (size <= INDEX_SIZE)
            throw new IllegalArgumentException(
                "Disk size must be > " + INDEX_SIZE);
        this.size  = size;
        this.cells = new String[size];
        clear();
    }

    // ── File operations ───────────────────────────────────────────────────────

    /**
     * Creates an empty file and registers it in the file index.
     *
     * @param name file name (must not already exist on the disk)
     * @return {@code true} if the file was created; {@code false} if the name
     *         already exists or the disk is full
     */
    public boolean createFile(String name) {
        if (directory.containsKey(name)) return false;
        if (nextDataAddress >= size)     return false;
        DiskFile f = new DiskFile(name, nextDataAddress);
        directory.put(name, f);
        nextDataAddress++;
        rebuildIndex();
        return true;
    }

    /**
     * Writes content to a named file, creating it if it does not exist.
     *
     * @param name    target filename
     * @param content string content to store
     * @return {@code true} on success; {@code false} if the file could not be created
     */
    public boolean writeFile(String name, String content) {
        if (!directory.containsKey(name)) createFile(name);
        DiskFile f = directory.get(name);
        if (f == null) return false;
        f.setContent(content);
        int addr = f.getStartAddress();
        // Store content as one cell
        if (addr < size) cells[addr] = content;
        rebuildIndex();
        return true;
    }

    /**
     * Reads and returns the content of a named file.
     *
     * @param name target filename
     * @return file content string, or {@code null} if the file does not exist
     */
    public String readFile(String name) {
        DiskFile f = directory.get(name);
        if (f == null) return null;
        return f.getContent();
    }

    /**
     * Deletes a file and removes its index entry.
     *
     * @param name filename to delete
     * @return {@code true} if the file was found and deleted; {@code false} if not found
     */
    public boolean deleteFile(String name) {
        DiskFile f = directory.remove(name);
        if (f == null) return false;
        cells[f.getStartAddress()] = "";
        rebuildIndex();
        return true;
    }

    /**
     * Returns {@code true} if a file with the given name exists in the directory.
     *
     * @param name filename to check
     * @return {@code true} if found
     */
    public boolean fileExists(String name) { return directory.containsKey(name); }

    // ── Program storage ───────────────────────────────────────────────────────

    /**
     * Stores an assembled program (list of instructions) as a file on disk.
     * The filename is the program name; each instruction occupies one disk cell.
     *
     * @param programName   name under which the program is stored
     * @param instructions  assembled instruction list
     */
    public void storeProgram(String programName, List<Instruction> instructions) {
        if (nextDataAddress + instructions.size() >= size) return;

        DiskFile f = new DiskFile(programName, nextDataAddress);
        directory.put(programName, f);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < instructions.size(); i++) {
            cells[nextDataAddress + i] = instructions.get(i).getOriginal();
            if (i > 0) sb.append('\n');
            sb.append(instructions.get(i).getOriginal());
        }
        f.setContent(sb.toString());
        f.setSize(instructions.size());
        nextDataAddress += instructions.size() + 1; // +1 for separation
        rebuildIndex();
    }

    // ── Index ─────────────────────────────────────────────────────────────────

    /** Rewrites the first INDEX_SIZE cells with the current file index. */
    private void rebuildIndex() {
        // Clear index area
        for (int i = 0; i < INDEX_SIZE; i++) cells[i] = "";
        int slot = 0;
        for (Map.Entry<String, DiskFile> e : directory.entrySet()) {
            if (slot >= INDEX_SIZE) break;
            cells[slot++] = e.getKey() + ":" + e.getValue().getStartAddress();
        }
    }

    // ── Raw access ────────────────────────────────────────────────────────────

    /**
     * Reads a raw disk cell by absolute address.
     *
     * @param address cell index (0 ≤ address &lt; size)
     * @return cell content, or empty string for out-of-bounds addresses
     */
    public String read(int address) {
        return (address >= 0 && address < size) ? cells[address] : "";
    }

    public void clear() {
        Arrays.fill(cells, "");
        directory.clear();
        nextDataAddress = INDEX_SIZE;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** Returns the total number of cells in this disk. */
    public int      getSize()      { return size; }

    /** Returns a direct reference to the disk cell array (use read-only for display). */
    public String[] getCells()     { return cells; }

    /**
     * Returns an unmodifiable view of the file directory (name → DiskFile).
     *
     * @return read-only directory map
     */
    public Map<String, DiskFile> getDirectory() {
        return Collections.unmodifiableMap(directory);
    }
}
