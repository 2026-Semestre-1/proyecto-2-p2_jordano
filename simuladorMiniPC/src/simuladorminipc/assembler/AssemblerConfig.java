package simuladorminipc.assembler;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads assembler configuration from
 * {@code simuladorminipc/assembler/assembler-config.json} on the classpath.
 * <p>
 * Edit that JSON file to change the instruction limit; the simulator will
 * pick it up the next time it is launched. If the file is absent or
 * contains invalid values the built-in default is used silently.
 * </p>
 *
 * <pre>
 * {
 *   "maxInstructions": 80   // maximum number of instructions per program
 * }
 * </pre>
 *
 * Set {@code maxInstructions} to 0 (or omit it and use 0 as default override)
 * to disable the limit entirely and let the memory manager decide.
 */
public final class AssemblerConfig {

    // ── Default ───────────────────────────────────────────────────────────────
    /** Default instruction limit (matches the original hard-coded value). */
    public static final int DEFAULT_MAX_INSTRUCTIONS = 80;

    // ── Config value (immutable after construction) ───────────────────────────
    /**
     * Maximum number of instructions allowed per program.
     * A value of 0 means no limit (memory availability is the only constraint).
     */
    public final int maxInstructions;

    // ── Constructor ───────────────────────────────────────────────────────────

    private AssemblerConfig(int maxInstructions) {
        this.maxInstructions = Math.max(0, maxInstructions);
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Loads configuration from the classpath JSON file.
     * Falls back to {@link #defaults()} if the file is not found or is invalid.
     */
    public static AssemblerConfig load() {
        try (InputStream is = AssemblerConfig.class
                .getResourceAsStream("assembler-config.json")) {
            if (is == null) return defaults();
            String json = new String(is.readAllBytes());
            int max = readInt(json, "maxInstructions", DEFAULT_MAX_INSTRUCTIONS);
            return new AssemblerConfig(max);
        } catch (Exception e) {
            return defaults();
        }
    }

    /** Returns a config object with the default value. */
    public static AssemblerConfig defaults() {
        return new AssemblerConfig(DEFAULT_MAX_INSTRUCTIONS);
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
        return "AssemblerConfig[maxInstructions=" + maxInstructions + "]";
    }
}
