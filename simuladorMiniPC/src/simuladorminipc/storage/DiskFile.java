package simuladorminipc.storage;

/**
 * Represents one file stored on the simulated disk.
 *
 * <p>Each {@code DiskFile} records the file name, its starting index in the
 * disk cell array, how many cells it occupies, and its raw text content.</p>
 *
 * @see simuladorminipc.storage.Disk
 */
public class DiskFile {

    private final String name;
    private final int    startAddress;  // starting index in the disk cells array
    private int          size;          // number of cells consumed
    private String       content;       // raw text content

    /**
     * Creates a new, empty disk file entry.
     *
     * @param name         file name (must be unique within the disk directory)
     * @param startAddress index in the disk cell array where this file’s data begins
     */
    public DiskFile(String name, int startAddress) {
        this.name         = name;
        this.startAddress = startAddress;
        this.content      = "";
        this.size         = 0;
    }

    /** Returns the file name. */
    public String getName()         { return name; }

    /** Returns the starting cell index in the disk array. */
    public int    getStartAddress() { return startAddress; }

    /** Returns the number of disk cells occupied by this file. */
    public int    getSize()         { return size; }

    /** Sets the number of cells this file occupies. */
    public void   setSize(int s)    { size = s; }

    /** Returns the raw text content of the file. */
    public String getContent()      { return content; }

    /**
     * Sets the content and automatically updates the size to the number of
     * characters ({@code 0} if content is {@code null}).
     *
     * @param c new content string (may be null to clear)
     */
    public void   setContent(String c) {
        content = c;
        size    = c == null ? 0 : c.length();
    }

    @Override
    public String toString() {
        return "DiskFile[" + name + " @" + startAddress + " sz=" + size + "]";
    }
}
