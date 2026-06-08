package simuladorminipc.model;

/**
 * Represents a pending or active I/O request issued by a process.
 * Used by the {@link simuladorminipc.io.IOManager} to track in-progress
 * operations and calculate when to unblock the owning process.
 *
 * <p>The request counts down its remaining time by one per
 * {@link #tick()} call; it is complete when
 * {@link #isComplete()} returns {@code true}.</p>
 *
 * @see simuladorminipc.io.IOManager
 * @see simuladorminipc.io.Device
 */
public class IORequest {

    public enum Operation { READ, WRITE, CREATE, DELETE, KEYBOARD, SCREEN }

    private final String    deviceId;
    private final Operation operation;
    private final String    filename;     // null for KEYBOARD / SCREEN
    private final int       duration;     // total ticks required
    private int             remainingTime;
    private int             dataValue;    // AL value for READ/WRITE
    private String          dataString;   // DX string for filename ops

    /**
     * Creates a request without a filename (used for KEYBOARD / SCREEN operations).
     *
     * @param deviceId  identifier of the target device
     * @param operation I/O operation type
     * @param duration  number of ticks the operation requires
     */
    public IORequest(String deviceId, Operation operation, int duration) {
        this(deviceId, operation, null, duration);
    }

    /**
     * Creates a request with an associated filename (used for file-system operations).
     *
     * @param deviceId  identifier of the target device (e.g. "DISK")
     * @param operation I/O operation type (CREATE, READ, WRITE, DELETE)
     * @param filename  target filename; may be {@code null} for non-file operations
     * @param duration  number of ticks the operation requires
     */
    public IORequest(String deviceId, Operation operation, String filename, int duration) {
        this.deviceId      = deviceId;
        this.operation     = operation;
        this.filename      = filename;
        this.duration      = duration;
        this.remainingTime = duration;
    }

    /**
     * Decrements the remaining-time counter by one tick.
     * Has no effect once the counter has reached zero.
     */
    public void tick() {
        if (remainingTime > 0) remainingTime--;
    }

    /**
     * Returns {@code true} when the remaining time has reached zero and the
     * operation can be considered finished.
     *
     * @return {@code true} if complete
     */
    public boolean isComplete()       { return remainingTime <= 0; }

    public String    getDeviceId()    { return deviceId; }
    public Operation getOperation()   { return operation; }
    public String    getFilename()    { return filename; }
    public int       getDuration()    { return duration; }
    public int       getRemainingTime(){ return remainingTime; }
    public int       getDataValue()   { return dataValue; }
    public void      setDataValue(int v){ dataValue = v; }
    public String    getDataString()  { return dataString; }
    public void      setDataString(String s){ dataString = s; }

    @Override
    public String toString() {
        return "IORequest[dev=" + deviceId + " op=" + operation
             + " file=" + filename + " remaining=" + remainingTime + "]";
    }
}
