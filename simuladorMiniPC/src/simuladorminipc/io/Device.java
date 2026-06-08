package simuladorminipc.io;

import simuladorminipc.model.IORequest;

/**
 * Represents a physical or logical I/O device in the simulated system.
 * <p>
 * Devices have a service time (ticks required per operation) and may hold
 * at most one active request at a time.
 * </p>
 *
 * <p>Devices are ticked each simulation cycle by the
 * {@link simuladorminipc.io.IOManager}; when a request’s remaining time
 * reaches zero it is flagged as complete.</p>
 *
 * @see IOManager
 * @see simuladorminipc.model.IORequest
 */
public class Device {

    public enum Type { KEYBOARD, SCREEN, DISK }

    private final String id;
    private final Type   type;
    private final int    serviceTime;   // base ticks per operation

    private IORequest currentRequest;

    /**
     * Constructs a device.
     *
     * @param id          unique device identifier (e.g. "KEYBOARD", "DISK")
     * @param type        device category
     * @param serviceTime base number of ticks required to complete one I/O operation
     */
    public Device(String id, Type type, int serviceTime) {
        this.id          = id;
        this.type        = type;
        this.serviceTime = serviceTime;
    }

    /**
     * Assigns an I/O request to this device.
     * Replaces any previously assigned (and presumably completed) request.
     *
     * @param request the request to service
     */
    public void assign(IORequest request) {
        this.currentRequest = request;
    }

    /** Advances the active request by one tick. */
    public void tick() {
        if (currentRequest != null) currentRequest.tick();
    }

    /**
     * Returns {@code true} if the current request has completed and clears the slot.
     * Must be called after each {@link #tick()} to detect completion.
     *
     * @return {@code true} when the in-progress request finished this tick
     */
    public boolean checkComplete() {
        if (currentRequest != null && currentRequest.isComplete()) {
            currentRequest = null;
            return true;
        }
        return false;
    }

    /** Returns {@code true} if a request is currently being serviced. */
    public boolean      isBusy()          { return currentRequest != null; }

    /** Returns the currently active request, or {@code null} if the device is idle. */
    public IORequest    getCurrentRequest(){ return currentRequest; }

    /** Returns the unique device identifier. */
    public String       getId()            { return id; }

    /** Returns the device type category. */
    public Type         getType()          { return type; }

    /** Returns the base number of ticks required per I/O operation. */
    public int          getServiceTime()   { return serviceTime; }

    /** Clears any active request. Called during system reset. */
    public void reset() { currentRequest = null; }

    @Override
    public String toString() {
        return "Device[" + id + " type=" + type
             + (isBusy() ? " BUSY" : " idle") + "]";
    }
}
