package simuladorminipc.storage;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads disk-related configuration from
 * {@code simuladorminipc/storage/disk-config.json} on the classpath.
 *
 * <pre>
 * {
 *   "diskSize": 512,
 *   "virtualMemorySize": 64
 * }
 * </pre>
 */
public final class DiskConfig {

    public static final int DEFAULT_DISK_SIZE = 512;
    public static final int DEFAULT_VMEM_SIZE = 64;

    /** Total size of secondary storage in cells. */
    public final int diskSize;

    /** Logical virtual memory size per process in words. */
    public final int virtualMemorySize;

    private DiskConfig(int diskSize, int virtualMemorySize) {
        this.diskSize = Math.max(Disk.INDEX_SIZE + 1, diskSize);
        this.virtualMemorySize = Math.max(1, virtualMemorySize);
    }

    public static DiskConfig load() {
        try (InputStream is = DiskConfig.class.getResourceAsStream("disk-config.json")) {
            if (is == null) return defaults();
            String json = new String(is.readAllBytes());
            int disk = readInt(json, "diskSize", DEFAULT_DISK_SIZE);
            int vmem = readInt(json, "virtualMemorySize", DEFAULT_VMEM_SIZE);
            return new DiskConfig(disk, vmem);
        } catch (Exception e) {
            return defaults();
        }
    }

    public static DiskConfig defaults() {
        return new DiskConfig(DEFAULT_DISK_SIZE, DEFAULT_VMEM_SIZE);
    }

    private static int readInt(String json, String key, int fallback) {
        Pattern p = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); }
            catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    @Override
    public String toString() {
        return "DiskConfig[disk=" + diskSize + " vmem=" + virtualMemorySize + "]";
    }
}
