package simuladorminipc.assembler;

/**
 * Every instruction type supported by the mini-assembler,
 * together with its execution weight (CPU cycles / ticks).
 * Weights follow the project specification table.
 *
 * <p>The weight of an instruction determines how many CPU ticks it occupies:
 * a weight of 1 completes in a single tick, a weight of 3 takes three ticks.</p>
 *
 * @see simuladorminipc.assembler.Instruction
 * @see simuladorminipc.cpu.CPU
 */
public enum InstructionType {

    // Data movement
    LOAD  (2),   // LOAD  reg  – loads register value into AC
    STORE (2),   // STORE reg  – stores AC into register
    MOV   (1),   // MOV   dst, src|val

    // Arithmetic
    ADD   (3),   // ADD   reg  – AC = AC + reg
    SUB   (3),   // SUB   reg  – AC = AC - reg
    INC   (1),   // INC [reg]  – increment AC or register by 1
    DEC   (1),   // DEC [reg]  – decrement AC or register by 1

    // Register exchange
    SWAP  (1),   // SWAP  reg1, reg2

    // Interrupts
    INT_20H (2), // Terminate process
    INT_10H (2), // Print DX to screen
    INT_09H (3), // Read keyboard to DX  (weight marked INT in spec → 3)
    INT_21H (5), // File operations via AH/AL/DX

    // Control flow
    JMP (2),     // JMP  [+/-offset]
    CMP (2),     // CMP  reg1, reg2
    JE  (2),     // JE   [+/-offset]
    JNE (2),     // JNE  [+/-offset]

    // Stack / subroutine params
    PARAM (3),   // PARAM v1 [,v2 [,v3]]  – max 3 values pushed onto stack
    PUSH  (1),   // PUSH reg
    POP   (1);   // POP  reg

    private final int weight;

    /**
     * Associates a CPU-cycle weight with this instruction type.
     *
     * @param weight number of CPU ticks required to execute this instruction
     */
    InstructionType(int weight) { this.weight = weight; }

    /**
     * Returns the execution weight (number of CPU ticks) for this instruction.
     *
     * @return weight ≥ 1
     */
    public int getWeight() { return weight; }
}
