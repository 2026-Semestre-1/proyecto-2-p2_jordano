package simuladorminipc.memory;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads memory-size configuration from
 * {@code simuladorminipc/memory/memory-config.json} on the classpath.
 * <p>
 * Edit that JSON file to change the simulated sizes; the simulator will
 * pick them up the next time it is launched.  If the file is absent or
 * contains invalid values the built-in defaults are used silently.
 * </p>
 *
 * <pre>
 * {
 *   "ramSize": 512   // cells of physical RAM
 * }
 * </pre>
 *
 * Disk size is configured independently by {@code simuladorminipc.storage.DiskConfig}.
 */
public final class MemoryConfig {

    // ── Defaults ─────────────────────────────────────────────────────────────
    public static final int DEFAULT_RAM  = 512;
    // ── Config values (immutable after construction) ──────────────────────────
    /** Physical RAM size in cells. */
    public final int ramSize;
    // ── Constructor ───────────────────────────────────────────────────────────

    private MemoryConfig(int ram) {
        this.ramSize = Math.max(RAM.OS_RESERVED + 1, ram);
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Loads configuration from the classpath JSON file.
     * Falls back to {@link #defaults()} if the file is not found or is invalid.
     */
    public static MemoryConfig load() {
        try (InputStream is = MemoryConfig.class
                .getResourceAsStream("memory-config.json")) {
            if (is == null) return defaults();
            String json = new String(is.readAllBytes());
            int ram  = readInt(json, "ramSize",           DEFAULT_RAM);
            return new MemoryConfig(ram);
        } catch (Exception e) {
            return defaults();
        }
    }

    /** Returns a config object with all default values. */
    public static MemoryConfig defaults() {
        return new MemoryConfig(DEFAULT_RAM);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int readInt(String json, String key, int fallback) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); }
            catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "MemoryConfig[ram=" + ramSize + "]";
    }
}
