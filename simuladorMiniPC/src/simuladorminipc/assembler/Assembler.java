package simuladorminipc.assembler;

import java.io.*;
import java.util.*;

/**
 * Lexer + parser for the mini-assembler language defined in the project spec.
 * <p>
 * Supported instructions: LOAD, STORE, MOV, ADD, SUB, INC, DEC, SWAP,
 * INT (20H/10H/09H/21H), JMP, CMP, JE, JNE, PARAM, PUSH, POP.
 * Lines starting with {@code ;} are treated as comments.
 * </p>
 *
 * <h3>Error handling</h3>
 * All parse errors throw a checked {@link Exception} with a human-readable
 * message that includes the source line number.
 *
 * <h3>Security note</h3>
 * File access is restricted to {@link java.io.File} objects supplied by the
 * caller; no path-traversal occurs inside this class.
 *
 * @see Instruction
 * @see InstructionType
 */
public class Assembler {

    private static final Set<String> REGISTERS = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList("AC","AX","BX","CX","DX","AH","AL")));

    private static final AssemblerConfig CONFIG = AssemblerConfig.load();

    /**
     * Parses a {@code .asm} file and returns the validated instruction list.
     *
     * @param file source file (must exist and be readable)
     * @return ordered list of decoded instructions
     * @throws Exception with a clear message if any syntax error is found
     */
    public static List<Instruction> parse(File file) throws Exception {
        if (file == null || !file.exists())
            throw new Exception("File does not exist: " + (file != null ? file.getPath() : "null"));

        List<Instruction> result = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String raw;
            int lineNo = 0;

            while ((raw = br.readLine()) != null) {
                lineNo++;
                // Strip inline comment and trim
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith(";")) continue;
                // Remove trailing comment
                int commentIdx = line.indexOf(';');
                if (commentIdx >= 0) line = line.substring(0, commentIdx).trim();
                if (line.isEmpty()) continue;

                result.add(parseLine(line, lineNo));
            }
        }

        if (result.isEmpty())
            throw new Exception("El archivo está vacío o no contiene instrucciones válidas.");

        int maxInstr = CONFIG.maxInstructions;
        if (maxInstr > 0 && result.size() > maxInstr)
            throw new Exception("El programa excede el límite de " + maxInstr +
                " instrucciones configurado en assembler-config.json.");

        return Collections.unmodifiableList(result);
    }

    // ── Line parser ──────────────────────────────────────────────────────────

    private static Instruction parseLine(String line, int lineNo) throws Exception {
        // Tokenize: split opcode from the rest, then handle operands
        String[] tokens = line.split("\\s+", 2);
        String opcode = tokens[0].toUpperCase();
        String rest   = tokens.length > 1 ? tokens[1].trim() : "";

        switch (opcode) {

            // ── Interrupt group ──────────────────────────────────────────────
            case "INT": {
                if (rest.isEmpty())
                    err(lineNo, "INT requires an interrupt code (20H, 10H, 09H or 21H).");
                String code = rest.split("\\s+")[0].toUpperCase();
                switch (code) {
                    case "20H": return make(InstructionType.INT_20H, "", "", null, line, lineNo);
                    case "10H": return make(InstructionType.INT_10H, "", "", null, line, lineNo);
                    case "09H": return make(InstructionType.INT_09H, "", "", null, line, lineNo);
                    case "21H": return make(InstructionType.INT_21H, "", "", null, line, lineNo);
                    default:    err(lineNo, "Unknown interrupt code '" + code +
                                    "'. Valid: 20H, 10H, 09H, 21H.");
                }
            }

            // ── MOV ──────────────────────────────────────────────────────────
            case "MOV": {
                String[] ops = splitOperands(rest, lineNo, 2, "MOV reg, reg|val");
                String dst = ops[0].toUpperCase();
                String src = ops[1];
                requireRegister(dst, lineNo, "MOV destination");

                String strLit = null;
                if (!isRegister(src) && !isInteger(src)) {
                    // treat as filename string literal (for INT 21H / DX)
                    strLit = src;
                    if (!dst.equals("DX"))
                        err(lineNo, "String literals may only be assigned to DX.");
                }
                return make(InstructionType.MOV, dst, src, strLit, line, lineNo);
            }

            // ── LOAD / STORE / ADD / SUB ─────────────────────────────────────
            case "LOAD": {
                String reg = requireOneRegister(rest, lineNo, "LOAD reg");
                return make(InstructionType.LOAD, reg, "", null, line, lineNo);
            }
            case "STORE": {
                String reg = requireOneRegister(rest, lineNo, "STORE reg");
                return make(InstructionType.STORE, reg, "", null, line, lineNo);
            }
            case "ADD": {
                String reg = requireOneRegister(rest, lineNo, "ADD reg");
                return make(InstructionType.ADD, reg, "", null, line, lineNo);
            }
            case "SUB": {
                String reg = requireOneRegister(rest, lineNo, "SUB reg");
                return make(InstructionType.SUB, reg, "", null, line, lineNo);
            }

            // ── INC / DEC ────────────────────────────────────────────────────
            case "INC": {
                if (rest.isEmpty()) return make(InstructionType.INC, "", "", null, line, lineNo);
                String reg = rest.split("\\s+")[0].toUpperCase();
                requireRegister(reg, lineNo, "INC register");
                return make(InstructionType.INC, reg, "", null, line, lineNo);
            }
            case "DEC": {
                if (rest.isEmpty()) return make(InstructionType.DEC, "", "", null, line, lineNo);
                String reg = rest.split("\\s+")[0].toUpperCase();
                requireRegister(reg, lineNo, "DEC register");
                return make(InstructionType.DEC, reg, "", null, line, lineNo);
            }

            // ── SWAP ─────────────────────────────────────────────────────────
            case "SWAP": {
                String[] ops = splitOperands(rest, lineNo, 2, "SWAP reg1, reg2");
                requireRegister(ops[0].toUpperCase(), lineNo, "SWAP first register");
                requireRegister(ops[1].toUpperCase(), lineNo, "SWAP second register");
                return make(InstructionType.SWAP, ops[0].toUpperCase(), ops[1].toUpperCase(),
                            null, line, lineNo);
            }

            // ── JMP ──────────────────────────────────────────────────────────
            case "JMP": {
                String off = requireOneOffset(rest, lineNo, "JMP [+/-offset]");
                return make(InstructionType.JMP, off, "", null, line, lineNo);
            }

            // ── CMP ──────────────────────────────────────────────────────────
            case "CMP": {
                String[] ops = splitOperands(rest, lineNo, 2, "CMP reg1, reg2");
                requireRegister(ops[0].toUpperCase(), lineNo, "CMP first operand");
                requireRegister(ops[1].toUpperCase(), lineNo, "CMP second operand");
                return make(InstructionType.CMP,
                            ops[0].toUpperCase(), ops[1].toUpperCase(), null, line, lineNo);
            }

            // ── JE / JNE ─────────────────────────────────────────────────────
            case "JE": {
                String off = requireOneOffset(rest, lineNo, "JE [+/-offset]");
                return make(InstructionType.JE, off, "", null, line, lineNo);
            }
            case "JNE": {
                String off = requireOneOffset(rest, lineNo, "JNE [+/-offset]");
                return make(InstructionType.JNE, off, "", null, line, lineNo);
            }

            // ── PARAM ────────────────────────────────────────────────────────
            case "PARAM": {
                if (rest.isEmpty())
                    err(lineNo, "PARAM requires at least one numeric value.");
                // Split by comma, max 3 values
                String[] parts = rest.split(",");
                if (parts.length > 3)
                    err(lineNo, "PARAM supports a maximum of 3 parameters.");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    String v = parts[i].trim();
                    if (!isInteger(v))
                        err(lineNo, "PARAM value '" + v + "' is not a valid integer.");
                    if (i > 0) sb.append(',');
                    sb.append(v);
                }
                return make(InstructionType.PARAM, sb.toString(), "", null, line, lineNo);
            }

            // ── PUSH / POP ───────────────────────────────────────────────────
            case "PUSH": {
                String reg = requireOneRegister(rest, lineNo, "PUSH reg");
                return make(InstructionType.PUSH, reg, "", null, line, lineNo);
            }
            case "POP": {
                String reg = requireOneRegister(rest, lineNo, "POP reg");
                return make(InstructionType.POP, reg, "", null, line, lineNo);
            }

            default:
                err(lineNo, "Unknown instruction '" + opcode + "'.");
                return null; // unreachable
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    /**
     * Convenience factory that delegates to the {@link Instruction} constructor.
     *
     * @param t       instruction type
     * @param o1      first operand (may be empty)
     * @param o2      second operand (may be empty)
     * @param strLit  string literal payload, or {@code null}
     * @param original raw source line
     * @param ln      1-based source line number
     * @return a new immutable Instruction
     */    private static Instruction make(InstructionType t, String o1, String o2,
                                    String strLit, String original, int ln) {
        return new Instruction(t, o1, o2, strLit, original, ln);
    }

    /**
     * Throws a parse {@link Exception} with a standardised line-number prefix.
     *
     * @param line 1-based source line number
     * @param msg  human-readable error description
     * @throws Exception always
     */
    private static void err(int line, String msg) throws Exception {
        throw new Exception("Línea " + line + ": " + msg);
    }

    /** Returns {@code true} if {@code s} is a recognised register name (case-insensitive). */
    private static boolean isRegister(String s) {
        return REGISTERS.contains(s.toUpperCase());
    }

    /** Returns {@code true} if {@code s} can be parsed as a decimal integer. */
    private static boolean isInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        try { Integer.parseInt(s); return true; }
        catch (NumberFormatException e) { return false; }
    }

    /** Returns {@code true} if {@code s} is a valid signed integer offset (e.g. "+2", "-1", "3"). */
    private static boolean isOffset(String s) {
        if (s == null || s.isEmpty()) return false;
        String v = s.startsWith("+") ? s.substring(1) : s;
        return isInteger(v);
    }

    /**
     * Validates that {@code s} is a legal register name; throws a parse error if not.
     *
     * @param s     candidate register name
     * @param lineNo source line number for error reporting
     * @param ctx   short context description included in the error message
     * @throws Exception if {@code s} is not a valid register
     */
    private static void requireRegister(String s, int lineNo, String ctx) throws Exception {
        if (!isRegister(s))
            err(lineNo, "Invalid register '" + s + "' in " + ctx +
                ". Valid registers: AC, AX, BX, CX, DX, AH, AL.");
    }

    /**
     * Extracts and validates a single register operand from {@code rest}.
     *
     * @param rest    operand string (everything after the opcode)
     * @param lineNo  source line number
     * @param usage   instruction usage string for error messages
     * @return upper-case register name
     * @throws Exception if no register is present or the token is not a register
     */
    private static String requireOneRegister(String rest, int lineNo, String usage)
            throws Exception {
        if (rest.isEmpty()) err(lineNo, usage + " requires a register operand.");
        String reg = rest.split("[,\\s]+")[0].toUpperCase();
        requireRegister(reg, lineNo, usage);
        return reg;
    }

    /**
     * Extracts and validates a single integer offset operand.
     *
     * @param rest    operand string (everything after the opcode)
     * @param lineNo  source line number
     * @param usage   instruction usage string for error messages
     * @return offset string (e.g. "+2", "-1")
     * @throws Exception if no valid offset is present
     */
    private static String requireOneOffset(String rest, int lineNo, String usage)
            throws Exception {
        if (rest.isEmpty()) err(lineNo, usage + " requires a numeric offset.");
        String off = rest.split("\\s+")[0];
        if (!isOffset(off))
            err(lineNo, "'" + off + "' is not a valid offset in " + usage + ".");
        return off;
    }

    /**
     * Splits operands by comma, trimming whitespace from each token.
     *
     * @param rest     operand string following the opcode
     * @param lineNo   source line number
     * @param expected minimum number of operands required
     * @param usage    instruction usage string for error messages
     * @return trimmed operand array
     * @throws Exception if fewer than {@code expected} operands are found
     */
    private static String[] splitOperands(String rest, int lineNo, int expected, String usage)
            throws Exception {
        if (rest.isEmpty())
            err(lineNo, usage + " requires " + expected + " operand(s).");
        String[] parts = rest.split(",");
        if (parts.length < expected)
            err(lineNo, "'" + rest + "' – expected " + expected +
                " operand(s) in " + usage + ".");
        String[] trimmed = new String[parts.length];
        for (int i = 0; i < parts.length; i++)
            trimmed[i] = parts[i].trim();
        return trimmed;
    }
}
