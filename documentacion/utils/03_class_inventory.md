# 03 — Class Inventory

---

## 1. MainFrame

| Property | Value |
|----------|-------|
| Package | `simuladorminipc` |
| Responsibility | JVM bootstrap. Sole `main()` entry point. Delegates to `SimuladorApp`. |

**Attributes:** none  
**Methods:**

| Signature | Purpose |
|-----------|---------|
| `static void main(String[] args)` | Calls `SimuladorApp.main(args)`. |

**Relationships:** → uses `fx.SimuladorApp`

---

## 2. SimuladorApp

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.fx` |
| Extends | `javafx.application.Application` |
| Responsibility | Full JavaFX scene graph construction. Wires all UI controls to `SimuladorController`. |

**Key Attributes:**

| Name | Type | Purpose |
|------|------|---------|
| `ctrl` | `SimuladorController` | The MVC controller instance |
| `btnLoad` | `Button` | Opens file-chooser dialog |
| `btnStart` | `Button` | Toggles full auto-run |
| `btnProcAuto` | `Button` | Runs until one process finishes |
| `btnStep` | `Button` | Single tick advance |
| `btnStop` | `Button` | Pauses auto-run |
| `btnReset` | `Button` | Full simulation reset |
| `btnInfo` | `Button` | Shows info/about dialog |
| `lblTick` | `Label` | Bound to `ctrl.currentTickProperty()` |
| `lblAlgoStatus` | `Label` | Bound to `ctrl.policyLabelProperty()` |
| `tfKeyboard` | `TextField` | User input for INT 09H |
| `btnKeyboard` | `Button` | Submits keyboard value |
| `logView` | `ListView<String>` | Event log display |
| `cpuIndicator` | `Circle` | Green/grey CPU busy indicator |
| `lblCpuStatus` | `Label` | "IDLE" / "RUNNING" |
| `lblCpuProcess` | `Label` | Current process name |

**Key Methods:**

| Signature | Purpose |
|-----------|---------|
| `void start(Stage stage)` | JavaFX entry; builds full scene, shows window |
| `Node buildToolbar(Stage stage)` | Toolbar with all control buttons |
| `Node buildLeftPanel()` | Vertical split section with active BCP/registers on top and event log below |
| `Node buildCenterPanel()` | Scrollable vertical stack of process queues |
| `Node buildRightPanel()` | RAM memory map `TableView` + disk map |
| `Node buildBottomPanel()` | Event log panel with embedded keyboard input row |
| `void bindControls()` | Binds all `ObservableList`s and `Property`s from controller to UI nodes |
| `void doLoad(Stage stage)` | Opens multi-file `FileChooser`, calls `ctrl.loadFiles()` |
| `void doReset()` | Calls `ctrl.reset()`, updates UI |
| `void showInfoDialog(Stage stage)` | Shows algorithm description modal |

**Relationships:** → owns `SimuladorController`; binds to all its `Property`/`ObservableList` fields

---

## 3. SimuladorController

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.fx` |
| Implements | `kernel.KernelEventListener` |
| Responsibility | MVC Controller. Owns `Kernel`. Translates Kernel events to JavaFX observable state changes. Provides simulation commands to the View. |

**Attributes:**

| Name | Type | Purpose |
|------|------|---------|
| `kernel` | `Kernel` | The simulation model |
| `currentTick` | `LongProperty` | Bound to tick label |
| `policyLabel` | `StringProperty` | Bound to algorithm badge |
| `autoRunning` | `BooleanProperty` | True while Timeline is running |
| `finished` | `BooleanProperty` | True when all processes done |
| `waitingKeyboard` | `BooleanProperty` | True while blocked on INT 09H |
| `kbInterruptedAuto` | `boolean` | Whether keyboard paused auto-run |
| `eventLog` | `ObservableList<String>` | Lines in the event log |
| `cpuStatus` | `StringProperty` | "IDLE" or "RUNNING" |
| `cpuProcess` | `StringProperty` | Running process name |
| `regPC..regAL` | `StringProperty` (×9) | Individual register values |
| `stackInfo` | `StringProperty` | Stack depth display |
| `cpuBurstInfo` | `StringProperty` | Burst and remaining ticks |
| `newQueueRows` | `ObservableList<ProcessRow>` | NEW queue table data |
| `readyQueueRows` | `ObservableList<ProcessRow>` | READY queue table data |
| `blockedQueueRows` | `ObservableList<ProcessRow>` | BLOCKED queue table data |
| `terminatedQueueRows` | `ObservableList<ProcessRow>` | TERMINATED queue table data |
| `memoryRows` | `ObservableList<MemoryRow>` | RAM map table data |
| `diskRows` | `ObservableList<DiskRow>` | Disk map table data |
| `statCpuUtil..statIdle` | `StringProperty` (×8) | Live statistics display values |
| `autoTimeline` | `Timeline` | Fires `step()` every 400 ms |
| `singleProcRunning` | `BooleanProperty` | "Run to next finish" mode flag |
| `singleProcBaseTerminated` | `int` | Baseline terminated count for single-proc mode |

**Methods:**

| Signature | Purpose |
|-----------|---------|
| `SimuladorController()` | Loads config, creates Kernel, boots, seeds memory/disk views |
| `List<String> loadFiles(List<File>)` | Calls `Kernel.loadProgram()` per file; returns error list |
| `void step()` | Calls `kernel.executeTick()` once; stops auto if finished |
| `void toggleAuto()` | Starts or stops the `Timeline` |
| `void startSingleProcess()` | Runs until one more process terminates |
| `void stopSingleProcess()` | Stops single-proc mode |
| `void reset()` | Stops animation, resets kernel, clears all observable data |
| `void setPolicy(String)` | Converts UI string to `Policy` enum, calls `kernel.setPolicy()` |
| `void setQuantum(int)` | Updates `kernel.setQuantum()` |
| `void provideKeyboardInput(int)` | Forwards user value to `kernel.provideKeyboardInput()` |
| `void onTickCompleted(long, PCB)` | KernelEventListener: refreshes all UI panels |
| `void onProcessStateChanged(PCB, ProcessState, ProcessState)` | Logs state transition |
| `void onScreenOutput(String)` | Appends to event log |
| `void onKeyboardInputRequired(PCB)` | Shows keyboard input row; pauses auto |
| `void onProcessFinished(PCB)` | Logs process finish |
| `void onAllProcessesFinished(StatisticsManager)` | Logs final stats; sets `finished=true` |
| `void onExecutionError(PCB, String)` | Logs error; highlights process |
| `void refreshQueues()` | Rebuilds all four queue `ObservableList`s from Kernel state |
| `void refreshMemory()` | Rebuilds RAM `ObservableList` |
| `void refreshDisk()` | Rebuilds disk `ObservableList` |
| `void refreshStats()` | Updates all statistic `StringProperty`s |
| `void refreshCpu(PCB)` | Updates CPU panel properties |
| `void log(String)` | Appends timestamped entry to `eventLog` |

**Inner Classes:**

| Class | Fields | Purpose |
|-------|--------|---------|
| `ProcessRow` | pid, name, state, priority, burst, waiting, pc, cpuId | One row in a process queue TableView |
| `MemoryRow` | address, value, owner | One RAM cell row |
| `DiskRow` | address, zone, value | One disk cell row |

**Relationships:** → `Kernel` (owns), implements `KernelEventListener`

---

## 4. Assembler

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.assembler` |
| Responsibility | Static factory: parses `.asm` text files into `List<Instruction>`. Full lexical + semantic validation. |

**Attributes (static constants):**

| Name | Type | Value |
|------|------|-------|
| `REGISTERS` | `Set<String>` | `{AC,AX,BX,CX,DX,AH,AL}` |
| `CONFIG` | `AssemblerConfig` | loaded from `assembler-config.json`; `maxInstructions` defaults to 80 (0 = no limit) |

**Methods (all static):**

| Signature | Purpose |
|-----------|---------|
| `List<Instruction> parse(File)` throws `Exception` | Entry point. Reads file line by line, strips comments, calls `parseLine()`. Validates count. |
| `Instruction parseLine(String line, int lineNo)` | Tokenises line; dispatches to per-opcode case. |
| `String[] splitOperands(String, int, int, String)` | Splits comma-separated operands; validates count. |
| `String requireOneRegister(String, int, String)` | Validates single register operand. |
| `void requireRegister(String, int, String)` | Throws if token is not a valid register name. |
| `boolean isRegister(String)` | Returns true if token is in `REGISTERS`. |
| `boolean isInteger(String)` | Returns true if token can be parsed as `int`. |
| `Instruction make(InstructionType, String, String, String, String, int)` | Factory: creates `Instruction`. |
| `void err(int, String)` throws `Exception` | Throws formatted error with line number. |

**Relationships:** → creates `Instruction`, uses `InstructionType`

---

## 5. Instruction

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.assembler` |
| Responsibility | Immutable value object representing one decoded assembly instruction. |

**Attributes:**

| Name | Type | Purpose |
|------|------|---------|
| `type` | `InstructionType` | Decoded opcode |
| `operand1` | `String` | First operand (upper-cased) |
| `operand2` | `String` | Second operand (upper-cased) |
| `stringLiteral` | `String` | Filename for DX (INT 21H), or null |
| `original` | `String` | Raw source line text |
| `weight` | `int` | CPU ticks required (from `type.getWeight()`) |
| `lineNumber` | `int` | 1-based source line number |

**Methods:** All getters. `toString()` returns `original`.

**Relationships:** created by `Assembler`; consumed by `CPU`; stored in `PCB`

---

## 6. InstructionType

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.assembler` |
| Responsibility | Enum of all valid opcodes, each with its CPU execution weight. |

**Values with weights:**

| Opcode | Weight | Description |
|--------|--------|-------------|
| `LOAD` | 2 | Load register into AC |
| `STORE` | 2 | Store AC into register |
| `MOV` | 1 | Move register-to-register or immediate |
| `ADD` | 3 | AC = AC + register |
| `SUB` | 3 | AC = AC − register |
| `INC` | 1 | Increment AC or register |
| `DEC` | 1 | Decrement AC or register |
| `SWAP` | 1 | Exchange two registers |
| `INT_20H` | 2 | Terminate process |
| `INT_10H` | 2 | Print DX to screen |
| `INT_09H` | 3 | Read keyboard into DX |
| `INT_21H` | 5 | File I/O via AH/AL/DX |
| `JMP` | 2 | Unconditional jump |
| `CMP` | 2 | Set zero flag |
| `JE` | 2 | Jump if zero flag set |
| `JNE` | 2 | Jump if zero flag clear |
| `PARAM` | 3 | Push up to 3 values onto stack |
| `PUSH` | 1 | Push register onto stack |
| `POP` | 1 | Pop stack into register |

---

## 7. SystemClock

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.clock` |
| Responsibility | Monotonically increasing integer tick counter. |

**Attributes:**

| Name | Type | Purpose |
|------|------|---------|
| `currentTick` | `long` | Current simulation time |

**Methods:**

| Signature | Purpose |
|-----------|---------|
| `long tick()` | Increments by 1, returns new value |
| `long getCurrentTick()` | Returns without incrementing |
| `void reset()` | Sets back to 0 |

---

## 8. CPU

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.cpu` |
| Responsibility | Simulates one CPU execution unit. Implements instruction-level latency via weight countdown. |

**Attributes:**

| Name | Type | Purpose |
|------|------|---------|
| `id` | `int` | CPU slot number (1) |
| `currentProcess` | `PCB` | Process currently on the CPU (null = idle) |
| `cyclesLeftForCurrentInstruction` | `int` | Weight countdown for current instruction |
| `busyCycles` | `long` | Cumulative non-idle cycles |

**Methods:**

| Signature | Purpose |
|-----------|---------|
| `CPU(int id)` | Constructor |
| `void loadProcess(PCB)` | Assigns process to CPU |
| `PCB releaseProcess()` | Removes current process; resets countdown |
| `boolean isIdle()` | Returns `currentProcess == null` |
| `CycleEvent executeCycle()` | One tick: fetch, weight-countdown, apply instruction |
| `CycleEvent applyInstruction(Instruction, RegisterSet)` | (private) Executes all instruction types |
| `int parseOffset(String)` | (private) Handles +/- jump offsets |
| `PCB getCurrentProcess()` | Getter |
| `int getId()` | Getter |
| `long getBusyCycles()` | Getter |

**Relationships:** → uses `PCB`, `RegisterSet`, `Instruction`, `InstructionType`; returns `CycleEvent`

---

## 9. CycleEvent

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.cpu` |
| Responsibility | Immutable value object returned by `CPU.executeCycle()`. Carries result code + optional payload. |

**Attributes:**

| Name | Type | Purpose |
|------|------|---------|
| `result` | `CycleResult` | Outcome code |
| `screenText` | `String` | Text for INT 10H |
| `errorMessage` | `String` | Description for ERROR |
| `fileSubcode` | `int` | AH value for INT 21H |
| `filename` | `String` | DX string for INT 21H |

**Methods (static factories):** `normal()`, `idle()`, `processFinished()`, `keyboardInput()`, `screenOutput(String)`, `fileOperation(int, String)`, `error(String)`.  
**Getters:** `getResult()`, `getScreenText()`, `getErrorMessage()`, `getFileSubcode()`, `getFilename()`.

---

## 10. CycleResult

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.cpu` |
| Responsibility | Enum of CPU tick outcomes. |

**Values:** `NORMAL`, `PROCESS_FINISHED`, `SCREEN_OUTPUT`, `KEYBOARD_INPUT`, `FILE_OPERATION`, `IDLE`, `ERROR`

---

## 11. Dispatcher

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.cpu` |
| Responsibility | Performs context switches. Only class allowed to load/unload PCBs from the CPU. Counts switches. |

**Attributes:**

| Name | Type | Purpose |
|------|------|---------|
| `contextSwitches` | `long` | Cumulative context switch count |

**Methods:**

| Signature | Purpose |
|-----------|---------|
| `void dispatch(PCB next, CPU cpu)` | Loads `next` into CPU; increments counter if process changed |
| `PCB preempt(CPU cpu)` | Removes current process; CPU becomes idle |
| `long getContextSwitches()` | Returns counter |

---

## 12. Kernel

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.kernel` |
| Responsibility | Central OS orchestrator. Owns all subsystems. Drives tick-by-tick simulation pipeline. |

**Attributes (all private final subsystems):**

| Name | Type |
|------|------|
| `cpu` | `CPU` |
| `dispatcher` | `Dispatcher` |
| `clock` | `SystemClock` |
| `queueManager` | `QueueManager` |
| `stateManager` | `StateManager` |
| `processManager` | `ProcessManager` |
| `policyManager` | `SchedulingPolicyManager` |
| `interruptManager` | `InterruptManager` |
| `ram` | `RAM` |
| `memoryManager` | `MemoryManager` |
| `vmManager` | `VirtualMemoryManager` |
| `swapManager` | `SwapManager` |
| `disk` | `Disk` |
| `ioManager` | `IOManager` |
| `statisticsManager` | `StatisticsManager` |
| `listeners` | `List<KernelEventListener>` |
| `booted` | `boolean` |
| `halted` | `boolean` |
| `waitingForKeyboard` | `PCB` |

**Public Methods:**

| Signature | Purpose |
|-----------|---------|
| `Kernel(int ramSize, int diskSize)` | Wires all subsystems |
| `Kernel()` | Defaults: RAM=512, Disk=512 |
| `void boot()` | Resets clock, PID counter; sets `booted=true` |
| `PCB loadProgram(File, int arrivalTime, int priority)` | Parses ASM, creates PCB, submits |
| `PCB loadProcess(String, List<Instruction>, int, int)` | Programmatic load |
| `void executeTick()` | Full simulation cycle (10 stages) |
| `boolean allProcessesFinished()` | Termination check |
| `void provideKeyboardInput(PCB, int value)` | Unblocks keyboard-waiting process |
| `void addListener / removeListener` | Observer registration |
| `void clearHalt()` | Allows re-run after completion |
| `void admitWaitingProcesses()` | Force admission pass |
| `QueueManager getQueueManager()` | Getter for controller introspection |
| `boolean isWaitingForKeyboard()` | Query keyboard-wait state |

**Private Methods:**

| Signature | Purpose |
|-----------|---------|
| `void scheduleAndDispatch(long tick)` | Runs scheduler, dispatches process |
| `void handleCycleEvent(CycleEvent, PCB, long)` | Routes CPU result to subsystems |
| `void handleQuantumExpiry(PCB)` | Preempts process after RR quantum |
| `fire*()` (×8) | Event broadcast helpers |

---

## 13. KernelEventListener

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.kernel` |
| Responsibility | Observer interface for Kernel → GUI decoupling. |

**Methods:**

| Signature | Purpose |
|-----------|---------|
| `void onTickCompleted(long tick, PCB running)` | End-of-tick notification |
| `void onProcessStateChanged(PCB, ProcessState old, ProcessState new)` | State transition |
| `void onScreenOutput(String text)` | INT 10H output |
| `void onKeyboardInputRequired(PCB process)` | INT 09H block started |
| `void onProcessFinished(PCB process)` | Process terminated |
| `void onAllProcessesFinished(StatisticsManager stats)` | Simulation complete |
| `void onExecutionError(PCB process, String message)` | Runtime error |

---

## 14. RAM

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.memory` |
| Responsibility | Physical RAM simulation as string-cell array. |

**Attributes:**

| Name | Type | Purpose |
|------|------|---------|
| `OS_RESERVED` (static) | `int` = 20 | OS-reserved cells at start |
| `size` | `int` | Total cell count |
| `cells` | `String[]` | Cell storage |

**Methods:** `read(int)`, `write(int, String)`, `clear()`, `clearRange(int, int)`, `getSize()`, `getCells()`, `userStart()` (= 20), `userEnd()` (= size-1).

---

## 15. MemoryManager

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.memory` |
| Responsibility | First-fit physical memory allocator. Manages OS area BCP summaries. |

**Attributes:**

| Name | Type | Purpose |
|------|------|---------|
| `ram` | `RAM` | Backing store |
| `allocated` | `List<MemoryBlock>` | All currently allocated blocks |
| `nextFreeAddress` | `int` | Bump-pointer for allocation |

**Methods:**

| Signature | Purpose |
|-----------|---------|
| `boolean allocate(PCB)` | First-fit allocation; writes instructions to RAM; stores BCP in OS area |
| `void free(PCB)` | Clears RAM range; removes block; clears OS area entry |
| `int freeWords()` | Available user-space words |
| `List<MemoryBlock> getAllocated()` | Returns allocated block list |
| `void updateBcpOsArea(PCB)` | Refreshes OS-area BCP entry each tick |
| `int findFreeBlock(int size)` | (private) First-fit search |
| `void storeBcpInOsArea(PCB, int base)` | (private) Writes process summary to cells 0–19 |
| `void clearBcpFromOsArea(PCB)` | (private) Removes OS area entry |

---

## 16. MemoryBlock

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.memory` |
| Responsibility | Value object describing one allocated RAM region. |

**Attributes:** `base` (int), `size` (int), `pid` (int), `free` (boolean)  
**Methods:** `getBase()`, `getSize()`, `getLimit()` (= base+size-1), `getPid()`, `isFree()`, `setFree(boolean)`

---

## 17. MemoryConfig

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.memory` |
| Responsibility | Reads hardware-size configuration from classpath JSON. Falls back to defaults. |

**Attributes (public final):** `ramSize`, `virtualMemorySize`, `diskSize` (all `int`)  
**Methods:** `static MemoryConfig load()`, `static MemoryConfig defaults()`

---

## 18. VirtualMemoryManager

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.memory` |
| Responsibility | Address translation (virtual → physical). Currently a transparent pass-through. |

**Methods:** `int translate(PCB, int virtualAddr)` (= base + offset), `boolean isValid(PCB, int virtualAddr)`, `getPhysicalManager()`

---

## 19. SwapManager

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.memory` |
| Responsibility | Placeholder swap-space: tracks suspended processes. |

**Attributes:** `swappedOut` (`Queue<PCB>`)  
**Methods:** `swapOut(PCB, MemoryManager)`, `PCB swapIn(MemoryManager)`, `hasSwappedProcesses()`, `swappedCount()`

---

## 20. PCB

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.model` |
| Responsibility | Process Control Block — complete descriptor of one process including identity, state, scheduling data, memory bounds, registers, I/O lists, instructions, and statistics. |

**Attributes:**

| Name | Type | Purpose |
|------|------|---------|
| `pidGen` (static) | `AtomicInteger` | Auto-incrementing PID generator |
| `pid` | `int` | Unique process identifier |
| `name` | `String` | Process name (filename without .asm) |
| `state` | `ProcessState` | Current state in lifecycle |
| `arrivalTime` | `int` | Tick at which process entered system |
| `burstTime` | `int` | Total CPU ticks needed (sum of weights) |
| `remainingTime` | `int` | Burst time still to execute |
| `executedTime` | `int` | Cycles actually run |
| `priority` | `int` | Scheduling priority (lower = higher) |
| `quantumRemaining` | `int` | Remaining RR quantum ticks |
| `waitingTime` | `int` | Cumulative ticks in READY without CPU |
| `turnaroundTime` | `int` | Ticks from arrival to termination |
| `responseTime` | `int` | Ticks from arrival to first CPU use (-1 until set) |
| `serviceTime` | `int` | Actual CPU ticks used |
| `turnaroundRatio` | `double` | turnaroundTime / serviceTime |
| `memoryRequired` | `int` | Words to reserve |
| `memoryBase` | `int` | Physical base address in RAM |
| `memoryLimit` | `int` | Physical limit address |
| `registers` | `RegisterSet` | CPU register file for this process |
| `pendingIORequests` | `List<IORequest>` | Queued I/O requests |
| `currentIORequest` | `IORequest` | Active I/O request |
| `openFiles` | `List<String>` | Currently open file names |
| `instructions` | `List<Instruction>` | Parsed program |
| `cpuId` | `int` | CPU slot that ran this process |
| `startTime` | `LocalTime` | Wall clock first-run time |
| `endTime` | `LocalTime` | Wall clock termination time |
| `wallStartMillis` | `long` | System millis at start |
| `wallEndMillis` | `long` | System millis at end |
| `started` | `boolean` | Whether process has received CPU |
| `finished` | `boolean` | Whether process has terminated |
| `nextBCP` | `PCB` | Linked-list pointer (BCP chain) |

**Key Methods:** `setInstructions(List<Instruction>)` (computes burst time), `hasMoreInstructions()`, `incrementWaitingTime()`, `decrementQuantumRemaining()`, getters/setters for all fields, `static resetPidCounter()`

---

## 21. ProcessState

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.model` |
| Responsibility | Enum of all valid process lifecycle states. |

**Values:** `NEW`, `READY`, `RUNNING`, `BLOCKED`, `SUSPENDED_READY`, `SUSPENDED_BLOCKED`, `TERMINATED`  
Each has a Spanish `displayName` (`getDisplayName()`, `toString()`).

---

## 22. RegisterSet

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.model` |
| Responsibility | CPU register file per process: 9 integer registers, zero flag, fixed-size stack (5 slots), auxiliary DX string. |

**Attributes:**

| Name | Type | Purpose |
|------|------|---------|
| `STACK_SIZE` (static) | `int` = 5 | Maximum stack depth |
| `ac,ax,bx,cx,dx,ir,pc,ah,al` | `int` | Individual registers |
| `dxString` | `String` | String alias for DX (filenames) |
| `zeroFlag` | `boolean` | Set by CMP, read by JE/JNE |
| `stack` | `int[]` | Fixed-size stack (5 entries) |
| `stackPointer` | `int` | -1 = empty |

**Methods:** `push(int)` throws `StackOverflowError`, `pop()` throws `RuntimeException`, `isStackEmpty()`, `isStackFull()`, `getStackDepth()`, `peekStack()`, `copy()` (deep copy), `reset()`, getters/setters for each register, `static boolean isValidRegister(String)`, `int getRegister(String)`, `void setRegister(String, int)`

---

## 23. IORequest

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.model` |
| Responsibility | Represents one pending or active I/O operation with countdown timer. |

**Attributes:** `deviceId`, `operation` (`Operation` enum: READ, WRITE, CREATE, DELETE, KEYBOARD, SCREEN), `filename`, `duration`, `remainingTime`, `dataValue`, `dataString`  
**Methods:** `tick()`, `isComplete()`, getters/setters.

---

## 24. ProcessManager

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.process` |
| Responsibility | Admits NEW processes to memory when space available. Max 5 concurrent in memory. |

**Attributes:** `MAX_CONCURRENT_PROCESSES` = 5, `queueManager`, `stateManager`, `memoryManager`  
**Methods:** `submit(PCB)`, `loadArrivingProcesses(long tick)`, `allFinished()`, `getTotalSubmitted()`

---

## 25. QueueManager

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.process` |
| Responsibility | Owns all five process queues. Provides move/query helpers. |

**Attributes:**

| Name | Type |
|------|------|
| `newQueue` | `Queue<PCB>` (LinkedList) |
| `readyQueue` | `List<PCB>` (ArrayList) |
| `blockedQueue` | `List<PCB>` (ArrayList) |
| `suspendedQueue` | `List<PCB>` (ArrayList) |
| `terminatedQueue` | `List<PCB>` (ArrayList) |

**Methods:** `admitToNew(PCB)`, `moveToReady(PCB)`, `moveToBlocked(PCB)`, `moveToSuspended(PCB)`, `moveToTerminated(PCB)`, `removeFromAll(PCB)`, `getAllLive()`, getters for each queue.

---

## 26. StateManager

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.process` |
| Responsibility | Enforces OS process state machine transitions. Single place for all legal transitions. |

**Methods:**

| Signature | Legal transition |
|-----------|-----------------|
| `admitToReady(PCB)` | NEW → READY |
| `setRunning(PCB)` | READY → RUNNING (removes from queue) |
| `preemptToReady(PCB)` | RUNNING → READY |
| `blockOnIO(PCB)` | RUNNING → BLOCKED |
| `unblockToReady(PCB)` | BLOCKED → READY |
| `terminate(PCB)` | RUNNING → TERMINATED |
| `suspendReady(PCB)` | READY → SUSPENDED_READY |
| `suspendBlocked(PCB)` | BLOCKED → SUSPENDED_BLOCKED |
| `resumeFromSuspended(PCB)` | SUSPENDED_* → READY |
| `guardTransition(PCB, from, to)` | (private) throws `IllegalStateException` if wrong |

---

## 27–32. Scheduler Implementations

All implement `SchedulingAlgorithm`.

| Class | Policy | Preemptive | Selection criterion |
|-------|--------|-----------|---------------------|
| `FCFSScheduler` | FCFS | No | Earliest `arrivalTime` (tie: lowest PID) |
| `RoundRobinScheduler` | Round Robin | Yes | Front of ready queue when quantum expired |
| `SPNScheduler` | SPN/SJF | No | Shortest `burstTime` (tie: earliest arrival) |
| `SRTScheduler` | SRT | Yes | Shortest `remainingTime` (preempts current) |
| `HRRNScheduler` | HRRN | No | Highest `(waitingTime + burstTime) / burstTime` |
| `PriorityScheduler` | Priority | No | Lowest `priority` number (tie: earliest arrival) |

`RoundRobinScheduler` constructor takes `int quantum`; `getName()` returns `"Round Robin (q=N)"`.

---

## 33. SchedulingPolicyManager

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.scheduler` |
| Responsibility | Holds active algorithm; delegates selection; supports runtime switching. |

**Attributes:** `currentAlgorithm` (`SchedulingAlgorithm`), `currentPolicy` (`Policy`), `rrQuantum` (`int`)  
**Methods:** `setPolicy(Policy)`, `selectNext(List<PCB>, PCB)`, `setRoundRobinQuantum(int)`, `getCurrentAlgorithm()`, `getCurrentPolicy()`, `getRrQuantum()`, `getPolicyName()`

---

## 34. StatisticsManager

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.stats` |
| Responsibility | Real-time OS performance metrics. Updated per-tick and per process-event. |

**Attributes:** `queueManager`, `cpu`, `totalTicks`, `idleTicks`, `contextSwitches`, `completedStats` (`List<ProcessStats>`)  
**Methods:** `onTick(long)`, `onProcessFirstRun(PCB, long)`, `onProcessFinished(PCB, long)`, `setContextSwitches(long)`, `getCpuUtilization()`, `getThroughput()`, `getAvgWaitingTime()`, `getAvgTurnaroundTime()`, `getAvgResponseTime()`, `getContextSwitches()`, `getCompletedStats()`, `getTotalTicks()`, `getIdleTicks()`

---

## 35. ProcessStats

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.stats` |
| Responsibility | Immutable snapshot of a terminated process's performance metrics. |

**Attributes (all final):** `pid`, `name`, `priority`, `startTime`, `endTime`, `burstTime`, `waitingTime`, `turnaroundTime`, `responseTime`, `serviceTime`, `turnaroundRatio`, `durationMillis`  
**Constructor:** `ProcessStats(PCB p)` — copies all fields by value.  
**Methods:** all getters + `toString()`.

---

## 36. Device

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.io` |
| Responsibility | Simulates one I/O device with configurable latency. Holds at most one active request. |

**Attributes:** `id` (String), `type` (`Type` enum: KEYBOARD/SCREEN/DISK), `serviceTime` (int), `currentRequest` (`IORequest`)  
**Methods:** `assign(IORequest)`, `tick()`, `checkComplete()` → bool, `isBusy()`, `reset()`, getters.

---

## 37. IOManager

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.io` |
| Responsibility | Manages three devices; submits/completes I/O requests; ticks all devices each cycle. |

**Attributes:** `keyboard` (latency 3), `screen` (latency 1), `disk` (latency 5), `diskStorage` (`Disk`), `waitingProcesses` (`Map<Integer, PCB>`)  
**Methods:**

| Signature | Purpose |
|-----------|---------|
| `submitKeyboardRequest(PCB)` | Assigns MAX_VALUE duration request (never auto-completes) |
| `completeKeyboardRequest(PCB, int)` | Writes value to DX, advances PC, removes from waiting |
| `submitFileRequest(PCB, int subcode, String filename)` | Dispatches to Disk, queues process |
| `performScreenOutput(String)` | No-op (caller handles display) |
| `List<PCB> tick()` | Advances all device timers; returns newly unblocked processes |
| `buildFileRequest(int, String, int)` | (private) Constructs `IORequest` based on AH subcode |

---

## 38. Interrupt

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.interrupt` |
| Responsibility | Immutable interrupt descriptor. |

**Attributes:** `type` (`InterruptType`), `sourcePCB` (`PCB`), `payload` (`String`), `intValue` (`int`)  
**Methods:** Three constructors (type+PCB, type+PCB+String, full), getters.

---

## 39. InterruptManager

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.interrupt` |
| Responsibility | FIFO interrupt queue. |

**Attributes:** `queue` (`ArrayDeque<Interrupt>`)  
**Methods:** `raise(Interrupt)`, `poll()`, `peek()`, `hasPending()`, `pendingCount()`, `snapshot()`, `clear()`

---

## 40. InterruptType

**Values:** `PROCESS_FINISHED`, `QUANTUM_EXPIRED`, `IO_REQUEST`, `IO_FINISHED`, `PAGE_FAULT`, `KEYBOARD_INPUT`, `FILE_OPERATION`, `SCREEN_OUTPUT`, `RUNTIME_ERROR`

---

## 41. Disk

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.storage` |
| Responsibility | Simulates secondary storage. Maintains file directory and cell data area. |

**Attributes:** `INDEX_SIZE` = 10, `size`, `cells` (`String[]`), `directory` (`Map<String, DiskFile>`), `nextDataAddress`  
**Methods:** `createFile(String)`, `openFile(String)` → bool, `writeFile(String, String)` → bool, `readFile(String)` → String, `deleteFile(String)` → bool, `storeProgram(String, List<Instruction>)`, `getDirectory()`, `getCells()`, `getSize()`, `rebuildIndex()` (private)

---

## 42. DiskFile

| Property | Value |
|----------|-------|
| Package | `simuladorminipc.storage` |
| Responsibility | Value object for one file on disk. |

**Attributes (final):** `name` (String), `startAddress` (int); **mutable:** `size` (int), `content` (String)  
**Methods:** Getters + `setContent(String)` (auto-updates `size`), `setSize(int)`
