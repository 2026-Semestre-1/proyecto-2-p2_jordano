# 02 — Package Inventory

## Package Map

```
simuladorminipc
├── (root)
├── assembler
├── clock
├── cpu
├── fx
├── interrupt
├── io
├── kernel
├── memory
├── model
├── process
├── scheduler
├── stats
└── storage
```

---

## simuladorminipc (root)

**Responsibility:** Sole JVM bootstrap package. Contains the `main()` entry point that launches the JavaFX application.

| File | Role |
|------|------|
| `MainFrame.java` | Contains `main(String[])`. Delegates immediately to `SimuladorApp.main()`. |

---

## simuladorminipc.assembler

**Responsibility:** Lexical analysis and parsing of `.asm` source files. Converts raw text lines into typed, immutable `Instruction` objects. Performs complete syntax and semantic validation; throws `Exception` with line-number context on any error.

| File | Role |
|------|------|
| `Assembler.java` | Static parser: `parse(File)` → `List<Instruction>`. Tokenises each line, validates opcodes, operands, and register names. Enforces configurable instruction limit (read from `assembler-config.json` via `AssemblerConfig`). |
| `AssemblerConfig.java` | Configuration reader for the assembler. Loads `assembler-config.json` from the classpath; exposes `maxInstructions` (default 80; 0 = no limit). |
| `assembler-config.json` | Assembler configuration file. Defines `maxInstructions`. |
| `Instruction.java` | Immutable value object: stores `InstructionType`, two operand strings, optional `stringLiteral` (for filename DX), original source text, execution weight, and source line number. |
| `InstructionType.java` | Enum of all supported opcodes, each annotated with its CPU weight (ticks required). |

**Dependencies out:** `model.RegisterSet` (register name validation indirect via `Assembler`)  
**Consumed by:** `kernel.Kernel`, `model.PCB`

---

## simuladorminipc.clock

**Responsibility:** Monotonically increasing integer tick counter used as the system time reference throughout the simulation.

| File | Role |
|------|------|
| `SystemClock.java` | `tick()` → `long`. `reset()`. `getCurrentTick()`. |

**Dependencies out:** none  
**Consumed by:** `kernel.Kernel`, `stats.StatisticsManager`

---

## simuladorminipc.cpu

**Responsibility:** Simulates the single CPU execution unit. Implements the fetch-decode-execute cycle at instruction-level with per-instruction latency (weight countdown). Delegates context switches to `Dispatcher`.

| File | Role |
|------|------|
| `CPU.java` | Holds the currently loaded `PCB`. `executeCycle()` returns a `CycleEvent`. Tracks busy cycles. |
| `CycleEvent.java` | Immutable value object returned by `CPU.executeCycle()`. Carries `CycleResult` + optional payload (screen text, error message, file subcode, filename). |
| `CycleResult.java` | Enum: `NORMAL`, `PROCESS_FINISHED`, `SCREEN_OUTPUT`, `KEYBOARD_INPUT`, `FILE_OPERATION`, `IDLE`, `ERROR`. |
| `Dispatcher.java` | Loads a `PCB` into the CPU; counts context switches. `dispatch(PCB, CPU)`. `preempt(CPU)`. |

**Dependencies out:** `model.PCB`, `model.RegisterSet`, `assembler.Instruction`, `assembler.InstructionType`  
**Consumed by:** `kernel.Kernel`

---

## simuladorminipc.fx

**Responsibility:** Complete JavaFX user interface (View) and MVC Controller. Bridges the `Kernel` (Model) to the graphical display via observable properties and `KernelEventListener` callbacks.

| File | Role |
|------|------|
| `SimuladorApp.java` | `extends Application`. Builds the current scene graph: toolbar, left split section for active BCP and event log, center scrollable queue stack, and right RAM/disk panel. Uses draggable `SplitPane` dividers. |
| `SimuladorController.java` | Owns `Kernel`. Implements `KernelEventListener`. Exposes `loadFiles()`, `step()`, `toggleAuto()`, `startSingleProcess()`, `reset()`, `provideKeyboardInput()`. Holds all `ObservableList` and `Property` objects bound to UI controls. |

**Inner classes in SimuladorController:**
- `ProcessRow` — POJO for process queue `TableView` rows.
- `MemoryRow` — POJO for RAM map `TableView` rows.
- `DiskRow` — POJO for disk map `TableView` rows.

**Dependencies out:** `kernel.Kernel`, `kernel.KernelEventListener`, `model.PCB`, `model.ProcessState`, `model.RegisterSet`, `scheduler.SchedulingPolicyManager.Policy`, `stats.StatisticsManager`, `memory.RAM`, `memory.MemoryConfig`, `storage.Disk`, `storage.DiskFile`

---

## simuladorminipc.interrupt

**Responsibility:** Interrupt descriptor and FIFO queue. Raised by the CPU/Kernel on specific events; processed by the Kernel each tick.

| File | Role |
|------|------|
| `Interrupt.java` | Immutable: `InterruptType`, source `PCB`, optional `String` payload, optional `int` value. |
| `InterruptManager.java` | `ArrayDeque`-backed queue. `raise(Interrupt)`, `poll()`, `peek()`, `clear()`, `snapshot()`. |
| `InterruptType.java` | Enum: `PROCESS_FINISHED`, `QUANTUM_EXPIRED`, `IO_REQUEST`, `IO_FINISHED`, `PAGE_FAULT`, `KEYBOARD_INPUT`, `FILE_OPERATION`, `SCREEN_OUTPUT`, `RUNTIME_ERROR`. |

**Dependencies out:** `model.PCB`  
**Consumed by:** `kernel.Kernel`

---

## simuladorminipc.io

**Responsibility:** Simulates physical I/O devices with configurable latency. Routes process I/O requests to the appropriate device and reports completions each tick.

| File | Role |
|------|------|
| `Device.java` | Single device: holds one active `IORequest`, ticks it down, reports completion. Types: `KEYBOARD` (3 ticks), `SCREEN` (1 tick), `DISK` (5 ticks). |
| `IOManager.java` | Owns the three `Device` instances. `submitKeyboardRequest()`, `completeKeyboardRequest()`, `submitFileRequest()`, `tick()` → `List<PCB>` (newly unblocked). |

**Dependencies out:** `model.IORequest`, `model.PCB`, `storage.Disk`  
**Consumed by:** `kernel.Kernel`

---

## simuladorminipc.kernel

**Responsibility:** Central OS orchestrator. Owns all subsystem references. Drives the tick-by-tick simulation pipeline. Fires observer events to registered `KernelEventListener` instances.

| File | Role |
|------|------|
| `Kernel.java` | Creates/owns every subsystem. `boot()`, `loadProgram(File, int, int)`, `executeTick()`, `provideKeyboardInput(PCB, int)`, `allProcessesFinished()`. Internal pipeline: clock → admission → I/O tick → schedule+dispatch → CPU cycle → event handling → RR quantum → stats → notify. |
| `KernelEventListener.java` | Observer interface: `onTickCompleted`, `onProcessStateChanged`, `onScreenOutput`, `onKeyboardInputRequired`, `onProcessFinished`, `onAllProcessesFinished`, `onExecutionError`. |

**Dependencies out:** All simulation packages  
**Consumed by:** `fx.SimuladorController`

---

## simuladorminipc.memory

**Responsibility:** Full memory hierarchy simulation: physical RAM, first-fit allocation, BCP storage in OS area, virtual address translation, swap-space placeholder, and configuration loading.

| File | Role |
|------|------|
| `RAM.java` | String-cell array of configurable size. Cells 0–19 reserved for OS. `read()`, `write()`, `clearRange()`. |
| `MemoryManager.java` | First-fit allocator. `allocate(PCB)` → bool, `free(PCB)`, `freeWords()`, `updateBcpOsArea(PCB)`. |
| `MemoryBlock.java` | Value object: `base`, `size`, `pid`, `free` flag. |
| `MemoryConfig.java` | Reads `memory-config.json` from classpath. Falls back to defaults. `load()` factory method. |
| `VirtualMemoryManager.java` | Address translator: `translate(PCB, virtualAddress)` = base + offset. Validation via `isValid()`. |
| `SwapManager.java` | Placeholder: maintains a suspended-process queue. `swapOut(PCB, MemoryManager)`, `swapIn(MemoryManager)`. |

**Dependencies out:** `model.PCB`, `assembler.Instruction`  
**Consumed by:** `kernel.Kernel`, `process.ProcessManager`

---

## simuladorminipc.model

**Responsibility:** Core data model objects (pure POJOs). No business logic — all state transitions are enforced externally by `StateManager`.

| File | Role |
|------|------|
| `PCB.java` | Process Control Block. Complete per-process descriptor: identity, state, scheduling fields, statistics fields, memory bounds, register set, I/O lists, instruction list, accounting timestamps. Auto-incremented PID. |
| `ProcessState.java` | Enum: `NEW`, `READY`, `RUNNING`, `BLOCKED`, `SUSPENDED_READY`, `SUSPENDED_BLOCKED`, `TERMINATED`. Each has a Spanish `displayName`. |
| `RegisterSet.java` | CPU register file: `AC`, `AX`, `BX`, `CX`, `DX`, `IR`, `PC`, `AH`, `AL`, `zeroFlag`, fixed-size stack (5 slots), `dxString`. `push()`, `pop()`, `copy()`, `reset()`. |
| `IORequest.java` | Pending/active I/O operation: `deviceId`, `Operation` enum, `filename`, countdown `remainingTime`. `tick()`, `isComplete()`. |

**Dependencies out:** `assembler.Instruction`  
**Consumed by:** nearly every package

---

## simuladorminipc.process

**Responsibility:** Process lifecycle management: admission from new queue to memory, queue ownership, and legal state transition enforcement.

| File | Role |
|------|------|
| `ProcessManager.java` | `submit(PCB)`, `loadArrivingProcesses(tick)` (promotes NEW → READY when memory available; max 5 concurrent), `allFinished()`. |
| `QueueManager.java` | Owns all five queues (`newQueue`, `readyQueue`, `blockedQueue`, `suspendedQueue`, `terminatedQueue`). Move helpers: `moveToReady()`, `moveToBlocked()`, `moveToSuspended()`, `moveToTerminated()`, `removeFromAll()`, `getAllLive()`. |
| `StateManager.java` | Enforces legal transitions. `admitToReady()`, `setRunning()`, `preemptToReady()`, `blockOnIO()`, `unblockToReady()`, `terminate()`. Throws `IllegalStateException` on invalid transitions. |

**Dependencies out:** `model.PCB`, `model.ProcessState`, `memory.MemoryManager`  
**Consumed by:** `kernel.Kernel`

---

## simuladorminipc.scheduler

**Responsibility:** Pluggable CPU scheduling algorithms. The codebase keeps multiple Strategy implementations, while the current UI documents FCFS as the visible active policy.

| File | Role |
|------|------|
| `SchedulingAlgorithm.java` | Interface: `selectNextProcess(List<PCB>, PCB)` → `PCB`, `getName()`. |
| `SchedulingPolicyManager.java` | Holds current `SchedulingAlgorithm`. `setPolicy(Policy)`, `selectNext()`, `setRoundRobinQuantum()`. Enum `Policy`: `FCFS`, `ROUND_ROBIN`, `SPN`, `SRT`, `HRRN`, `PRIORITY`. |
| `FCFSScheduler.java` | Non-preemptive; selects earliest arrival, ties broken by lowest PID. |
| `RoundRobinScheduler.java` | Preemptive; picks front of queue when quantum expired (managed by Kernel). |
| `SPNScheduler.java` | Non-preemptive; selects shortest `burstTime`, ties by arrival. |
| `SRTScheduler.java` | Preemptive; selects shortest `remainingTime`, preempts current if better candidate arrives. |
| `HRRNScheduler.java` | Non-preemptive; selects highest `(waitingTime + burstTime) / burstTime` ratio. |
| `PriorityScheduler.java` | Non-preemptive; selects lowest priority number (= highest priority), ties by arrival. |

**Dependencies out:** `model.PCB`  
**Consumed by:** `kernel.Kernel`

---

## simuladorminipc.stats

**Responsibility:** Real-time performance statistics collection and aggregation.

| File | Role |
|------|------|
| `StatisticsManager.java` | `onTick(tick)` — increments waiting times for ready processes and idle/busy counters. `onProcessFirstRun(PCB, tick)` — captures response time. `onProcessFinished(PCB, tick)` — captures turnaround, service time, normalised ratio. Aggregates: CPU utilisation %, throughput, averages. |
| `ProcessStats.java` | Immutable snapshot of one terminated process's stats: pid, name, priority, startTime, endTime, burstTime, waitingTime, turnaroundTime, responseTime, serviceTime, turnaroundRatio, durationMillis. |

**Dependencies out:** `model.PCB`, `model.ProcessState`, `cpu.CPU`, `process.QueueManager`  
**Consumed by:** `kernel.Kernel`, `fx.SimuladorController`

---

## simuladorminipc.storage

**Responsibility:** Simulates secondary storage (disk) with a file directory and cell-array data area. Supports create, open, read, write, delete, and program-image storage.

| File | Role |
|------|------|
| `Disk.java` | Cell array + `LinkedHashMap<String, DiskFile>` directory. `createFile()`, `openFile()`, `writeFile()`, `readFile()`, `deleteFile()`, `storeProgram()`, `getDirectory()`, `getCells()`. First 10 cells are file index. |
| `DiskFile.java` | Value object: `name`, `startAddress`, `size`, `content`. |

**Dependencies out:** `assembler.Instruction`  
**Consumed by:** `io.IOManager`, `kernel.Kernel`, `fx.SimuladorController`
