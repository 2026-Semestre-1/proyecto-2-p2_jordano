package simuladorminipc.assembler;

/**
 * Immutable representation of one decoded assembler instruction.
 * <p>
 * Stores the instruction type, its operands (as raw upper-case strings),
 * the original source text, the execution weight, and a string operand
 * used by INT 21H filename operations.
 * </p>
 *
 * <p>Instances are created exclusively by {@link Assembler#parse(java.io.File)}
 * and are immutable once constructed.</p>
 *
 * @see InstructionType
 * @see simuladorminipc.cpu.CPU
 */
public final class Instruction {

    private final InstructionType type;
    private final String          operand1;     // first operand  (may be "")
    private final String          operand2;     // second operand (may be "")
    private final String          stringLiteral;// non-null when DX receives a filename
    private final String          original;     // raw source line
    private final int             weight;       // execution ticks
    private final int             lineNumber;   // source line for error messages

    /**
     * Constructs an immutable instruction descriptor.
     *
     * @param type          decoded instruction type (never null)
     * @param operand1      first operand string, upper-cased on storage; empty string if absent
     * @param operand2      second operand string, upper-cased on storage; empty string if absent
     * @param stringLiteral optional string payload for INT 21H filename/DX ops; {@code null} if unused
     * @param original      raw source line as it appeared in the .asm file
     * @param lineNumber    1-based source line number (used in error messages)
     */
    public Instruction(InstructionType type,
                       String operand1,
                       String operand2,
                       String stringLiteral,
                       String original,
                       int    lineNumber) {
        this.type          = type;
        this.operand1      = operand1      != null ? operand1.trim().toUpperCase()      : "";
        this.operand2      = operand2      != null ? operand2.trim().toUpperCase()      : "";
        this.stringLiteral = stringLiteral != null ? stringLiteral.trim()               : null;
        this.original      = original;
        this.weight        = type.getWeight();
        this.lineNumber    = lineNumber;
    }

    /** Returns the instruction type. */
    public InstructionType getType()          { return type; }

    /** Returns the first operand (upper-case), or an empty string if absent. */
    public String          getOperand1()      { return operand1; }

    /** Returns the second operand (upper-case), or an empty string if absent. */
    public String          getOperand2()      { return operand2; }

    /**
     * Returns the string literal payload used for DX/filename operations (INT 21H),
     * or {@code null} when not applicable.
     */
    public String          getStringLiteral() { return stringLiteral; }

    /** Returns the raw source line as it appeared in the .asm file. */
    public String          getOriginal()      { return original; }

    /**
     * Returns the execution weight (CPU ticks) for this instruction,
     * derived from {@link InstructionType#getWeight()}.
     *
     * @return weight ≥ 1
     */
    public int             getWeight()        { return weight; }

    /**
     * Returns the 1-based source line number, used to generate human-readable
     * parse and runtime error messages.
     *
     * @return line number ≥ 1
     */
    public int             getLineNumber()    { return lineNumber; }

    @Override
    public String toString() { return original; }
}
