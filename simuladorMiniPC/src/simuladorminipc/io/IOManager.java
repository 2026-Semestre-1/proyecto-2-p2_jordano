package simuladorminipc.io;

import simuladorminipc.model.IORequest;
import simuladorminipc.model.PCB;
import simuladorminipc.storage.Disk;

import java.util.*;

/**
 * Manages all I/O devices and tracks which blocked processes are waiting
 * for I/O completion.
 * <p>
 * Each tick the Kernel calls {@link #tick()} to advance in-progress I/O.
 * When an operation completes the owning PCB is returned so the Kernel can
 * move it back to the ready queue.
 * </p>
 */
public class IOManager {

    private final Device           keyboard;
    private final Device           screen;
    private final Device           disk;
    private final Disk             diskStorage;

    /** Maps PCB pid → IORequest currently being serviced for that process. */
    private final Map<Integer, PCB> waitingProcesses = new LinkedHashMap<>();

    /**
     * Constructs an IOManager with the given disk storage backend.
     * Creates three standard devices: KEYBOARD (latency 3), SCREEN (latency 1),
     * DISK (latency 5).
     *
     * @param diskStorage the simulated disk used for file operations
     */
    public IOManager(Disk diskStorage) {
        this.diskStorage = diskStorage;
        this.keyboard = new Device("KEYBOARD", Device.Type.KEYBOARD, 3);
        this.screen   = new Device("SCREEN",   Device.Type.SCREEN,   1);
        this.disk     = new Device("DISK",      Device.Type.DISK,     5);
    }

    // ── Request submission ────────────────────────────────────────────────────

    /**
     * Submits a keyboard-input request for {@code process}.
     * The process should already be in BLOCKED state.
     */
    public void submitKeyboardRequest(PCB process) {
        // Duration = MAX_VALUE so the request NEVER auto-completes via the tick
        // loop.  The Kernel completes it manually via completeKeyboardRequest()
        // only after the user provides input.
        IORequest req = new IORequest("KEYBOARD", IORequest.Operation.KEYBOARD,
                                      Integer.MAX_VALUE);
        process.setCurrentIORequest(req);
        keyboard.assign(req);
        // NOT added to waitingProcesses – keyboard completion is user-driven,
        // not timer-driven.
    }

    /**
     * Completes a pending keyboard request for a process, storing the
     * user-supplied value into DX.
     */
    public void completeKeyboardRequest(PCB process, int userValue) {
        process.getRegisters().setDx(userValue);
        // Advance PC past INT 09H now that input is available
        process.getRegisters().setPc(process.getRegisters().getPc() + 1);
        process.setCurrentIORequest(null);
        waitingProcesses.remove(process.getPid());
    }

    /**
     * Submits a file I/O request (INT 21H) for {@code process}.
     * Executes synchronously against the simulated Disk.
     *
     * @param process  owning process
     * @param subcode  AH value (0x3C create, 0x3D open, 0x4D read,
     *                           0x40 write, 0x41 delete)
     * @param filename DX string value
     */
    public void submitFileRequest(PCB process, int subcode, String filename) {
        IORequest req = buildFileRequest(subcode, filename, disk.getServiceTime());
        req.setDataValue(process.getRegisters().getAl());
        req.setDataString(String.valueOf(process.getRegisters().getAl()));
        process.setCurrentIORequest(req);
        disk.assign(req);
        waitingProcesses.put(process.getPid(), process);
    }

    /** Performs a screen-output operation (instant – no blocking). */
    public void performScreenOutput(String text) {
        // Caller (Kernel/GUI) is responsible for displaying the text;
        // this method is here for future device-level buffering.
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    /**
     * Advances all active device timers by one tick.
     *
     * @return list of PCBs whose I/O completed this tick (ready to unblock)
     */
    public List<PCB> tick() {
        keyboard.tick();
        screen.tick();
        disk.tick();

        List<PCB> completed = new ArrayList<>();

        for (Iterator<Map.Entry<Integer, PCB>> it = waitingProcesses.entrySet().iterator();
             it.hasNext(); ) {
            Map.Entry<Integer, PCB> entry = it.next();
            PCB     p   = entry.getValue();
            IORequest r = p.getCurrentIORequest();

            if (r != null && r.isComplete()) {
                // Execute the disk operation result
                applyFileResult(p, r);
                p.setCurrentIORequest(null);
                it.remove();
                completed.add(p);
            }
        }
        return completed;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a disk {@link IORequest} from an INT 21H sub-function code.
     *
     * @param subcode  AH value (0x3C create, 0x3D open, 0x4D read, 0x40 write, 0x41 delete)
     * @param filename target filename from DX string
     * @param duration service latency in ticks
     * @return configured IORequest
     */
    private IORequest buildFileRequest(int subcode, String filename, int duration) {
        IORequest.Operation op;
        switch (subcode) {
            case 0x3C: op = IORequest.Operation.CREATE; break;
            case 0x3D: op = IORequest.Operation.READ;   break;
            case 0x4D: op = IORequest.Operation.READ;   break;
            case 0x40: op = IORequest.Operation.WRITE;  break;
            case 0x41: op = IORequest.Operation.DELETE; break;
            default:   op = IORequest.Operation.READ;
        }
        return new IORequest("DISK", op, filename, duration);
    }

    /**
     * Executes the actual file-system side-effect when a disk I/O request completes.
     * Updates the process's open-file table and/or registers as needed.
     *
     * @param p the process whose I/O just finished
     * @param r the completed I/O request
     */
    private void applyFileResult(PCB p, IORequest r) {
        int subcode = p.getRegisters().getAh();
        String fname = r.getFilename();
        switch (subcode) {
            case 0x3C:
                diskStorage.createFile(fname);
                p.addOpenFile(fname);
                break;
            case 0x3D:
                if (diskStorage.fileExists(fname)) p.addOpenFile(fname);
                break;
            case 0x4D: {
                String content = diskStorage.readFile(fname);
                int val = 0;
                if (content != null && !content.isEmpty()) {
                    try { val = Integer.parseInt(content.trim()); }
                    catch (NumberFormatException ignored) { val = content.charAt(0); }
                }
                p.getRegisters().setAl(val);
                break;
            }
            case 0x40:
                diskStorage.writeFile(fname, String.valueOf(p.getRegisters().getAl()));
                break;
            case 0x41:
                diskStorage.deleteFile(fname);
                p.removeOpenFile(fname);
                break;
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** Returns the keyboard device. */
    public Device getKeyboard()   { return keyboard; }

    /** Returns the screen device. */
    public Device getScreen()     { return screen; }

    /** Returns the disk device (timing metadata). */
    public Device getDisk()       { return disk; }

    /** Returns the underlying disk storage used for file operations. */
    public Disk   getDiskStorage(){ return diskStorage; }

    /**
     * Returns an unmodifiable view of the map from PID to blocked PCBs
     * currently awaiting I/O completion.
     *
     * @return read-only process map (PID → PCB)
     */
    public Map<Integer, PCB> getWaitingProcesses() {
        return Collections.unmodifiableMap(waitingProcesses);
    }

    /** Clears all in-progress I/O state. Called during kernel reset. */
    public void reset() {
        waitingProcesses.clear();
        keyboard.reset();
        screen.reset();
        disk.reset();
    }
}
