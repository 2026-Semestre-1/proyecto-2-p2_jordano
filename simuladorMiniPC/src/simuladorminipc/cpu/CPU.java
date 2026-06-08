package simuladorminipc.cpu;

import simuladorminipc.assembler.Instruction;
import simuladorminipc.assembler.InstructionType;
import simuladorminipc.model.PCB;
import simuladorminipc.model.RegisterSet;

import java.util.List;

/**
 * Simulates the CPU execution unit (CPU1 in the project spec).
 * <p>
 * Each {@link #executeCycle()} call represents one clock tick.
 * Instructions with weight > 1 occupy multiple ticks before they take
 * effect – this models instruction latency as required by the rubric.
 * </p>
 *
 * <h3>Responsibilities (Single Responsibility)</h3>
 * <ul>
 *   <li>Fetch / decode / execute one instruction per cycle.</li>
 *   <li>Track instruction-level progress (weight countdown).</li>
 *   <li>Return a {@link CycleEvent} describing what happened.</li>
 * </ul>
 * Context-switching is delegated exclusively to {@link Dispatcher}.
 */
public class CPU {

    private final int id;           // CPU slot id (e.g. 1)

    private PCB  currentProcess;
    private int  cyclesLeftForCurrentInstruction; // weight countdown
    private long busyCycles;        // total non-idle cycles executed

    /**
     * Constructs a CPU unit with the specified slot identifier.
     *
     * @param id CPU slot number (e.g., 1 for "CPU-1")
     */
    public CPU(int id) {
        this.id = id;
    }

    // ── Process management ───────────────────────────────────────────────────

    /**
     * Loads a process into the CPU.
     * The CPU does NOT change the process state here; that is the Dispatcher's
     * or Kernel's responsibility.
     */
    public void loadProcess(PCB process) {
        this.currentProcess = process;
        this.cyclesLeftForCurrentInstruction = 0;
    }

    /**
     * Releases the current process from the CPU without terminating it.
     * Used during context switches.
     */
    public PCB releaseProcess() {
        PCB p = currentProcess;
        currentProcess = null;
        cyclesLeftForCurrentInstruction = 0;
        return p;
    }

    public boolean isIdle() { return currentProcess == null; }

    // ── Execution cycle ──────────────────────────────────────────────────────

    /**
     * Executes one clock tick.
     *
     * <ol>
     *   <li>Guard: return IDLE if no process is loaded.</li>
     *   <li>If PC is past the end of the instruction list, return PROCESS_FINISHED.</li>
     *   <li>Start a new instruction if weight countdown reached zero.</li>
     *   <li>Decrement the countdown; if still &gt; 0, return NORMAL (instruction in progress).</li>
     *   <li>When countdown reaches zero, apply the instruction's effect.</li>
     * </ol>
     *
     * @return {@link CycleEvent} describing the outcome of this tick.
     */
    public CycleEvent executeCycle() {
        if (currentProcess == null) return CycleEvent.idle();

        List<Instruction> instrs = currentProcess.getInstructions();
        RegisterSet regs = currentProcess.getRegisters();
        int pc = regs.getPc();

        if (pc < 0 || pc >= instrs.size()) {
            return CycleEvent.processFinished();
        }

        Instruction inst = instrs.get(pc);

        // Starting a new instruction
        if (cyclesLeftForCurrentInstruction == 0) {
            cyclesLeftForCurrentInstruction = inst.getWeight();
            regs.setIr(pc);
        }

        busyCycles++;
        cyclesLeftForCurrentInstruction--;

        if (cyclesLeftForCurrentInstruction > 0) {
            // Still consuming cycles for this instruction
            return CycleEvent.normal();
        }

        // Instruction latency exhausted – apply effect
        return applyInstruction(inst, regs);
    }

    // ── Instruction execution ────────────────────────────────────────────────

    private CycleEvent applyInstruction(Instruction inst, RegisterSet regs) {
        int pc = regs.getPc();

        switch (inst.getType()) {

            // ── LOAD reg → AC = reg ──────────────────────────────────────────
            case LOAD: {
                regs.setAc(regs.getRegister(inst.getOperand1()));
                regs.setPc(pc + 1);
                return CycleEvent.normal();
            }

            // ── STORE reg → reg = AC ─────────────────────────────────────────
            case STORE: {
                regs.setRegister(inst.getOperand1(), regs.getAc());
                regs.setPc(pc + 1);
                return CycleEvent.normal();
            }

            // ── MOV dst, src|val ─────────────────────────────────────────────
            case MOV: {
                String dst = inst.getOperand1();
                String src = inst.getOperand2();

                if (inst.getStringLiteral() != null) {
                    // MOV DX, filename
                    regs.setDxString(inst.getStringLiteral());
                } else {
                    int val = RegisterSet.isValidRegister(src)
                            ? regs.getRegister(src)
                            : Integer.parseInt(src);
                    regs.setRegister(dst, val);
                }
                regs.setPc(pc + 1);
                return CycleEvent.normal();
            }

            // ── ADD / SUB ────────────────────────────────────────────────────
            case ADD: {
                regs.setAc(regs.getAc() + regs.getRegister(inst.getOperand1()));
                regs.setPc(pc + 1);
                return CycleEvent.normal();
            }
            case SUB: {
                regs.setAc(regs.getAc() - regs.getRegister(inst.getOperand1()));
                regs.setPc(pc + 1);
                return CycleEvent.normal();
            }

            // ── INC / DEC ────────────────────────────────────────────────────
            case INC: {
                if (inst.getOperand1().isEmpty()) regs.setAc(regs.getAc() + 1);
                else regs.setRegister(inst.getOperand1(),
                                       regs.getRegister(inst.getOperand1()) + 1);
                regs.setPc(pc + 1);
                return CycleEvent.normal();
            }
            case DEC: {
                if (inst.getOperand1().isEmpty()) regs.setAc(regs.getAc() - 1);
                else regs.setRegister(inst.getOperand1(),
                                       regs.getRegister(inst.getOperand1()) - 1);
                regs.setPc(pc + 1);
                return CycleEvent.normal();
            }

            // ── SWAP ─────────────────────────────────────────────────────────
            case SWAP: {
                String r1 = inst.getOperand1(), r2 = inst.getOperand2();
                int tmp = regs.getRegister(r1);
                regs.setRegister(r1, regs.getRegister(r2));
                regs.setRegister(r2, tmp);
                regs.setPc(pc + 1);
                return CycleEvent.normal();
            }

            // ── INT 20H – terminate ──────────────────────────────────────────
            case INT_20H: {
                regs.setPc(pc + 1);
                return CycleEvent.processFinished();
            }

            // ── INT 10H – print screen (DX) ──────────────────────────────────
            case INT_10H: {
                String text = regs.getDxString() != null
                            ? regs.getDxString()
                            : String.valueOf(regs.getDx());
                regs.setPc(pc + 1);
                return CycleEvent.screenOutput(text);
            }

            // ── INT 09H – keyboard input → DX ────────────────────────────────
            case INT_09H: {
                // PC is NOT advanced yet; Kernel will advance it after input
                return CycleEvent.keyboardInput();
            }

            // ── INT 21H – file operations ─────────────────────────────────────
            case INT_21H: {
                int ah = regs.getAh();
                String fname = regs.getDxString() != null
                             ? regs.getDxString()
                             : String.valueOf(regs.getDx());
                regs.setPc(pc + 1);
                return CycleEvent.fileOperation(ah, fname);
            }

            // ── JMP ──────────────────────────────────────────────────────────
            case JMP: {
                int offset = parseOffset(inst.getOperand1());
                regs.setPc(pc + 1 + offset);
                return CycleEvent.normal();
            }

            // ── CMP ──────────────────────────────────────────────────────────
            case CMP: {
                int v1 = regs.getRegister(inst.getOperand1());
                int v2 = regs.getRegister(inst.getOperand2());
                regs.setZeroFlag(v1 == v2);
                regs.setPc(pc + 1);
                return CycleEvent.normal();
            }

            // ── JE ───────────────────────────────────────────────────────────
            case JE: {
                int offset = parseOffset(inst.getOperand1());
                regs.setPc(regs.isZeroFlag() ? pc + 1 + offset : pc + 1);
                return CycleEvent.normal();
            }

            // ── JNE ──────────────────────────────────────────────────────────
            case JNE: {
                int offset = parseOffset(inst.getOperand1());
                regs.setPc(!regs.isZeroFlag() ? pc + 1 + offset : pc + 1);
                return CycleEvent.normal();
            }

            // ── PARAM ────────────────────────────────────────────────────────
            case PARAM: {
                String[] vals = inst.getOperand1().split(",");
                for (String v : vals) {
                    try {
                        regs.push(Integer.parseInt(v.trim()));
                    } catch (StackOverflowError e) {
                        return CycleEvent.error("Stack overflow in PARAM at line "
                                + inst.getLineNumber());
                    }
                }
                regs.setPc(pc + 1);
                return CycleEvent.normal();
            }

            // ── PUSH ─────────────────────────────────────────────────────────
            case PUSH: {
                try {
                    regs.push(regs.getRegister(inst.getOperand1()));
                } catch (StackOverflowError e) {
                    return CycleEvent.error("Stack overflow in PUSH at line "
                            + inst.getLineNumber());
                }
                regs.setPc(pc + 1);
                return CycleEvent.normal();
            }

            // ── POP ──────────────────────────────────────────────────────────
            case POP: {
                try {
                    regs.setRegister(inst.getOperand1(), regs.pop());
                } catch (RuntimeException e) {
                    return CycleEvent.error("Stack underflow in POP at line "
                            + inst.getLineNumber());
                }
                regs.setPc(pc + 1);
                return CycleEvent.normal();
            }

            default:
                return CycleEvent.error("Unhandled instruction type: " + inst.getType());
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static int parseOffset(String s) {
        if (s.startsWith("+")) return Integer.parseInt(s.substring(1));
        return Integer.parseInt(s);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    /** Returns the CPU slot ID. */
    public int  getId()            { return id; }

    /** Returns the PCB currently loaded in the CPU, or {@code null} if idle. */
    public PCB  getCurrentProcess(){ return currentProcess; }

    /** Returns the cumulative number of non-idle ticks this CPU has executed. */
    public long getBusyCycles()    { return busyCycles; }

    /**
     * Returns the number of ticks remaining before the current instruction completes.
     *
     * @return countdown value (0 if idle or about to start a new instruction)
     */
    public int  getCyclesLeftForCurrentInstruction() {
        return cyclesLeftForCurrentInstruction;
    }

    @Override
    public String toString() {
        return "CPU-" + id + (isIdle() ? "[idle]"
                : "[pid=" + currentProcess.getPid() + "]");
    }
}
