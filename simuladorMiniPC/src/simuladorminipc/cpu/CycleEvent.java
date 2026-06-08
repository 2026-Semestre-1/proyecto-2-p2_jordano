package simuladorminipc.cpu;

/**
 * Value object returned by {@link CPU#executeCycle()}.
 * Carries both the result code and any associated data (screen text,
 * error message, file-operation subfunction).
 */
public final class CycleEvent {

    private final CycleResult result;
    private final String      screenText;    // SCREEN_OUTPUT: text to display
    private final String      errorMessage;  // ERROR: description
    private final int         fileSubcode;   // FILE_OPERATION: AH value
    private final String      filename;      // FILE_OPERATION: filename from DX

    /**
     * Constructs a cycle event.
     *
     * @param result       outcome code
     * @param screenText   text to display (SCREEN_OUTPUT only)
     * @param errorMessage error description (ERROR only)
     * @param fileSubcode  AH sub-function code (FILE_OPERATION only)
     * @param filename     filename from DX (FILE_OPERATION only)
     */
    private CycleEvent(CycleResult result,
                       String screenText, String errorMessage,
                       int fileSubcode, String filename) {
        this.result       = result;
        this.screenText   = screenText;
        this.errorMessage = errorMessage;
        this.fileSubcode  = fileSubcode;
        this.filename     = filename;
    }

    // ── Factory methods ──────────────────────────────────────────────────────

    /** Returns an event signalling normal instruction execution (no side effect). */
    public static CycleEvent normal()            { return new CycleEvent(CycleResult.NORMAL, null, null, 0, null); }

    /** Returns an event signalling that the CPU has no loaded process. */
    public static CycleEvent idle()              { return new CycleEvent(CycleResult.IDLE, null, null, 0, null); }

    /** Returns an event signalling that INT 20H was executed (process terminates). */
    public static CycleEvent processFinished()   { return new CycleEvent(CycleResult.PROCESS_FINISHED, null, null, 0, null); }

    /** Returns an event signalling that INT 09H was executed (keyboard block). */
    public static CycleEvent keyboardInput()     { return new CycleEvent(CycleResult.KEYBOARD_INPUT, null, null, 0, null); }

    /**
     * Returns an event carrying screen output text (INT 10H).
     *
     * @param text text to display on the simulated screen
     */
    public static CycleEvent screenOutput(String text) {
        return new CycleEvent(CycleResult.SCREEN_OUTPUT, text, null, 0, null);
    }

    /**
     * Returns an event carrying a file-system operation (INT 21H).
     *
     * @param subcode AH register value identifying the operation (e.g. 0x3C = create)
     * @param filename filename string from DX
     */
    public static CycleEvent fileOperation(int subcode, String filename) {
        return new CycleEvent(CycleResult.FILE_OPERATION, null, null, subcode, filename);
    }

    /**
     * Returns an error event with a descriptive message.
     *
     * @param msg human-readable error description
     */
    public static CycleEvent error(String msg) {
        return new CycleEvent(CycleResult.ERROR, null, msg, 0, null);
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    /** Returns the result code of this event. */
    public CycleResult getResult()      { return result; }

    /** Returns the screen text (only relevant when result is SCREEN_OUTPUT), or {@code null}. */
    public String      getScreenText()  { return screenText; }

    /** Returns the error message (only relevant when result is ERROR), or {@code null}. */
    public String      getErrorMessage(){ return errorMessage; }

    /** Returns the INT 21H sub-function code (AH value; only relevant when result is FILE_OPERATION). */
    public int         getFileSubcode() { return fileSubcode; }

    /** Returns the filename from DX (only relevant when result is FILE_OPERATION), or {@code null}. */
    public String      getFilename()    { return filename; }

    @Override
    public String toString() {
        return "CycleEvent[" + result
             + (screenText   != null ? " text="  + screenText : "")
             + (errorMessage != null ? " error=" + errorMessage : "")
             + (filename     != null ? " file="  + filename : "")
             + "]";
    }
}
