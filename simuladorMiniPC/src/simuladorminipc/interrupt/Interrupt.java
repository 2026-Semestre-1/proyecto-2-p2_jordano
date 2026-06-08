package simuladorminipc.interrupt;

import simuladorminipc.model.PCB;

/**
 * Immutable descriptor of a raised interrupt, carrying the type,
 * the process that caused it, and an optional payload string.
 *
 * <p>Interrupts are queued in the
 * {@link simuladorminipc.interrupt.InterruptManager} and processed by the
 * {@link simuladorminipc.kernel.Kernel} at the start of each tick.</p>
 *
 * @see InterruptType
 * @see InterruptManager
 */
public final class Interrupt {

    private final InterruptType type;
    private final PCB           sourcePCB;   // may be null (e.g. system interrupt)
    private final String        payload;     // context data (e.g. filename, error text)
    private final int           intValue;    // numeric payload (e.g. AH for INT 21H)

    /**
     * Creates an interrupt with only a type and source process.
     *
     * @param type      the interrupt type (never null)
     * @param sourcePCB the process that raised the interrupt, or {@code null} for system-level events
     */
    public Interrupt(InterruptType type, PCB sourcePCB) {
        this(type, sourcePCB, null, 0);
    }

    /**
     * Creates an interrupt with a type, source process, and string payload.
     *
     * @param type      the interrupt type
     * @param sourcePCB the process that raised the interrupt, or {@code null}
     * @param payload   context string (e.g. error message, filename); may be {@code null}
     */
    public Interrupt(InterruptType type, PCB sourcePCB, String payload) {
        this(type, sourcePCB, payload, 0);
    }

    /**
     * Full constructor.
     *
     * @param type      the interrupt type
     * @param sourcePCB the source process, or {@code null}
     * @param payload   optional string payload
     * @param intValue  optional numeric payload (e.g. AH value for INT 21H)
     */
    public Interrupt(InterruptType type, PCB sourcePCB, String payload, int intValue) {
        this.type      = type;
        this.sourcePCB = sourcePCB;
        this.payload   = payload;
        this.intValue  = intValue;
    }

    /** Returns the interrupt type. */
    public InterruptType getType()      { return type; }

    /** Returns the process that raised this interrupt, or {@code null} for system events. */
    public PCB           getSourcePCB() { return sourcePCB; }

    /** Returns the optional string payload, or {@code null} if not set. */
    public String        getPayload()   { return payload; }

    /** Returns the optional numeric payload (e.g. AH sub-function code). */
    public int           getIntValue()  { return intValue; }

    @Override
    public String toString() {
        return "Interrupt[" + type
             + (sourcePCB != null ? " pid=" + sourcePCB.getPid() : "")
             + (payload   != null ? " payload=" + payload : "")
             + "]";
    }
}
