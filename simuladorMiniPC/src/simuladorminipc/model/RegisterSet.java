package simuladorminipc.model;

import java.util.Arrays;

/**
 * Simulates the CPU register file of the minicomputer.
 * <p>
 * Contains: AC, AX, BX, CX, DX, IR, PC, AH, AL,
 * a zero-flag used by CMP/JE/JNE, a fixed-size stack (5 slots),
 * and an auxiliary dxString for INT 21H filename operations.
 * </p>
 */
public class RegisterSet {

    public static final int STACK_SIZE = 5;

    private int ac;       // Accumulator
    private int ax;       // General purpose A
    private int bx;       // General purpose B
    private int cx;       // General purpose C
    private int dx;       // General purpose D / I-O data
    private int ir;       // Instruction Register (index of current instruction)
    private int pc;       // Program Counter  (index of next instruction)
    private int ah;       // High sub-register (INT 21H subfunction codes)
    private int al;       // Low  sub-register (INT 21H content)

    /** String auxiliary for DX when used as filename in INT 21H. */
    private String dxString;

    /** Zero flag – set by CMP, read by JE / JNE. */
    private boolean zeroFlag;

    /** Fixed-size process stack. */
    private int[]   stack;
    private int     stackPointer;   // -1 = empty

    public RegisterSet() {
        stack = new int[STACK_SIZE];
        reset();
    }

    /** Resets every register, flag, and stack entry to zero / initial state. */
    public void reset() {
        ac = ax = bx = cx = dx = ir = pc = ah = al = 0;
        dxString    = null;
        zeroFlag    = false;
        Arrays.fill(stack, 0);
        stackPointer = -1;
    }

    // ── Stack ────────────────────────────────────────────────────────────────

    /**
     * Pushes a value onto the process stack.
     *
     * @param value integer value to push
     * @throws StackOverflowError if the stack is already full ({@link #STACK_SIZE} entries)
     */
    public void push(int value) {
        if (stackPointer >= STACK_SIZE - 1) {
            throw new StackOverflowError(
                "Stack overflow – maximum depth is " + STACK_SIZE);
        }
        stack[++stackPointer] = value;
    }

    /**
     * Pops and returns the top value from the process stack.
     *
     * @return the top stack value
     * @throws RuntimeException if the stack is empty
     */
    public int pop() {
        if (stackPointer < 0) {
            throw new RuntimeException("Stack underflow – stack is empty");
        }
        return stack[stackPointer--];
    }

    /** Returns {@code true} if the stack is empty. */
    public boolean isStackEmpty() { return stackPointer < 0; }

    /** Returns {@code true} if the stack is full. */
    public boolean isStackFull()  { return stackPointer >= STACK_SIZE - 1; }

    /** Returns the number of values currently on the stack. */
    public int     getStackDepth(){ return stackPointer + 1; }

    /** Returns the top value without popping, or 0 if the stack is empty. */
    public int     peekStack()    { return stackPointer >= 0 ? stack[stackPointer] : 0; }

    // ── Deep copy ────────────────────────────────────────────────────────────

    /**
     * Returns a deep copy of this register set.
     *
     * @return new {@link RegisterSet} with identical state
     */
    public RegisterSet copy() {
        RegisterSet c = new RegisterSet();
        c.ac = ac; c.ax = ax; c.bx = bx; c.cx = cx; c.dx = dx;
        c.ir = ir; c.pc = pc; c.ah = ah; c.al = al;
        c.dxString   = dxString;
        c.zeroFlag   = zeroFlag;
        c.stack      = Arrays.copyOf(stack, STACK_SIZE);
        c.stackPointer = stackPointer;
        return c;
    }

    /**
     * Overwrites this register set with a deep copy of another.
     *
     * @param o source register set to copy from
     */
    public void copyFrom(RegisterSet o) {
        ac = o.ac; ax = o.ax; bx = o.bx; cx = o.cx; dx = o.dx;
        ir = o.ir; pc = o.pc; ah = o.ah; al = o.al;
        dxString     = o.dxString;
        zeroFlag     = o.zeroFlag;
        stack        = Arrays.copyOf(o.stack, STACK_SIZE);
        stackPointer = o.stackPointer;
    }

    // ── Dynamic name-based access ────────────────────────────────────────────

    /**
     * Returns the value of a register addressed by name.
     *
     * @param name register name (case-insensitive; AC, AX, BX, CX, DX, IR, PC, AH, AL)
     * @return current integer value of the register
     * @throws IllegalArgumentException if the name does not match any register
     */
    public int getRegister(String name) {
        switch (name.toUpperCase()) {
            case "AC": return ac;
            case "AX": return ax;
            case "BX": return bx;
            case "CX": return cx;
            case "DX": return dx;
            case "IR": return ir;
            case "PC": return pc;
            case "AH": return ah;
            case "AL": return al;
            default: throw new IllegalArgumentException("Unknown register: " + name);
        }
    }

    /**
     * Sets the value of a register addressed by name.
     *
     * @param name  register name (case-insensitive; AC, AX, BX, CX, DX, IR, PC, AH, AL)
     * @param value new integer value
     * @throws IllegalArgumentException if the name does not match any register
     */
    public void setRegister(String name, int value) {
        switch (name.toUpperCase()) {
            case "AC": ac = value; break;
            case "AX": ax = value; break;
            case "BX": bx = value; break;
            case "CX": cx = value; break;
            case "DX": dx = value; break;
            case "IR": ir = value; break;
            case "PC": pc = value; break;
            case "AH": ah = value; break;
            case "AL": al = value; break;
            default: throw new IllegalArgumentException("Unknown register: " + name);
        }
    }

    /**
     * Returns {@code true} if the given string names a valid register.
     *
     * @param name register name to check (case-insensitive)
     * @return {@code true} if valid; {@code false} otherwise (including {@code null})
     */
    public static boolean isValidRegister(String name) {
        if (name == null) return false;
        switch (name.toUpperCase()) {
            case "AC": case "AX": case "BX": case "CX":
            case "DX": case "IR": case "PC": case "AH": case "AL":
                return true;
            default: return false;
        }
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public int  getAc()              { return ac; }
    public void setAc(int v)         { ac = v; }
    public int  getAx()              { return ax; }
    public void setAx(int v)         { ax = v; }
    public int  getBx()              { return bx; }
    public void setBx(int v)         { bx = v; }
    public int  getCx()              { return cx; }
    public void setCx(int v)         { cx = v; }
    public int  getDx()              { return dx; }
    public void setDx(int v)         { dx = v; }
    public int  getIr()              { return ir; }
    public void setIr(int v)         { ir = v; }
    public int  getPc()              { return pc; }
    public void setPc(int v)         { pc = v; }
    public int  getAh()              { return ah; }
    public void setAh(int v)         { ah = v; }
    public int  getAl()              { return al; }
    public void setAl(int v)         { al = v; }
    public String getDxString()      { return dxString; }
    public void setDxString(String s){ dxString = s; }
    public boolean isZeroFlag()      { return zeroFlag; }
    public void setZeroFlag(boolean f){ zeroFlag = f; }
    public int[] getStack()          { return Arrays.copyOf(stack, STACK_SIZE); }
    public int   getStackPointer()   { return stackPointer; }

    @Override
    public String toString() {
        return String.format(
            "PC=%d IR=%d AC=%d AX=%d BX=%d CX=%d DX=%d AH=%d AL=%d ZF=%b SP=%d",
            pc, ir, ac, ax, bx, cx, dx, ah, al, zeroFlag, stackPointer);
    }
}
