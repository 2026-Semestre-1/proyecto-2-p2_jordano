# 04 — Execution Flow (Technical)

---

## Overview: The 10-Stage Tick Pipeline

Every simulation cycle (`Kernel.executeTick()`) executes exactly 10 ordered stages:

```
Stage 1 : Clock advance
Stage 2 : Process admission (NEW → READY)
Stage 3 : I/O device tick (BLOCKED → READY on completion)
Stage 4 : Scheduler selection + CPU dispatch
Stage 5 : CPU cycle execution
Stage 6 : CycleEvent handling (route to subsystem)
Stage 7 : Round-Robin quantum check
Stage 8 : Statistics update
Stage 8b: BCP refresh in OS memory area
Stage 9 : Notify listeners (update GUI)
Stage 10: Termination check (halt if all done)
```

Pre-conditions: `booted == true`, `halted == false`, `waitingForKeyboard == null`.

---

## Phase A: File Loading

### A.1 — User Opens File Chooser

```
SimuladorApp.doLoad(Stage)
  └── FileChooser (multi-select, filter *.asm)
        └── ctrl.loadFiles(List<File>)
```

### A.2 — loadFiles() per file

```
SimuladorController.loadFiles(files)
  for each file:
    kernel.loadProgram(file, arrivalTime=0, priority=i+1)
      ├── Assembler.parse(file)           ← Phase B
      ├── Create PCB(name, arrival, prio, memNeeded=instrs.size+5)
      ├── pcb.setInstructions(instructions)  ← computes burstTime
      ├── disk.storeProgram(name, instructions)
      └── processManager.submit(pcb)
            └── queueManager.admitToNew(pcb)   state = NEW
    
  After all files loaded:
    kernel.clearHalt()
    kernel.admitWaitingProcesses()        ← forces one admission pass
    ctrl.refreshQueues/Memory/Disk/Stats()
```

---

## Phase B: Assembly Parsing (Assembler.parse)

```
Assembler.parse(File file)
  1. Validate file not null / exists
  2. Open BufferedReader
  3. For each line:
     a. trim()
     b. skip if empty or starts with ';'
     c. strip inline comment (everything after ';')
     d. call parseLine(line, lineNo)
        └── tokenise: split on whitespace (max 2 tokens: opcode + rest)
            └── switch(opcode.toUpperCase()):
                ├── INT    → check code ∈ {20H,10H,09H,21H}
                ├── MOV    → validate dst register; src = register|integer|stringLiteral
                ├── LOAD   → require one register operand
                ├── STORE  → require one register operand
                ├── ADD    → require one register operand
                ├── SUB    → require one register operand
                ├── INC    → optional register (defaults to AC)
                ├── DEC    → optional register (defaults to AC)
                ├── SWAP   → require two register operands
                ├── JMP    → require numeric offset string
                ├── CMP    → require two register operands
                ├── JE     → require numeric offset string
                ├── JNE    → require numeric offset string
                ├── PARAM  → require 1–3 comma-separated integers
                ├── PUSH   → require one register operand
                ├── POP    → require one register operand
                └── default → throw Exception("Unknown opcode")
            └── Instruction.make(...) → Instruction(type, op1, op2, strLit, original, lineNo)
  4. if result.isEmpty() → throw Exception (empty file)
  5. if result.size() > 80 → throw Exception (too many instructions)
  6. return Collections.unmodifiableList(result)

ERROR PATH: any Exception propagated to loadFiles() → added to error list → shown in GUI log
```

---

## Phase C: Memory Admission (NEW → READY)

```
ProcessManager.loadArrivingProcesses(currentTick)
  count = processes in READY + BLOCKED + RUNNING queues
  for each PCB in newQueue (snapshot):
    if pcb.arrivalTime > currentTick → skip
    if count >= 5 → break
    if memoryManager.allocate(pcb):
      stateManager.admitToReady(pcb)   state: NEW → READY
        └── queueManager.moveToReady(pcb)
      count++

MemoryManager.allocate(pcb):
  1. required = pcb.getMemoryRequired()
  2. base = findFreeBlock(required)   [first-fit scan of allocated list]
  3. if base == -1 → return false (not enough contiguous space)
  4. Write instructions to RAM cells [base .. base+instrs.size-1]
  5. storeBcpInOsArea(pcb, base)     [cells 0–19]
  6. pcb.setMemoryBase(base)
  7. pcb.setMemoryLimit(base + required - 1)
  8. pcb.getRegisters().setPc(0)
  9. allocated.add(new MemoryBlock(base, required, pid))
  10. updateNextFree()
  11. return true
```

---

## Phase D: I/O Device Tick (Stage 3)

```
IOManager.tick()
  keyboard.tick()    [counts down active keyboard request — in practice always MAX_VALUE]
  screen.tick()      [screen ops are instant / 1 tick]
  disk.tick()        [disk ops: 5 ticks]

  For each (pid → PCB) in waitingProcesses:
    if corresponding device.checkComplete():
      execute the actual file operation on Disk
      remove from waitingProcesses
      add PCB to unblocked list

  return unblocked list

Kernel receives unblocked list:
  for each unblocked PCB:
    stateManager.unblockToReady(p)    state: BLOCKED → READY
    interruptManager.raise(Interrupt(IO_FINISHED, p))
    fireStateChanged(p, BLOCKED, READY)
```

---

## Phase E: Scheduling and Dispatch (Stage 4)

```
Kernel.scheduleAndDispatch(tick)
  readyQueue = queueManager.getReadyQueue()
  currentProcess = cpu.getCurrentProcess()

  next = policyManager.selectNext(readyQueue, currentProcess)
    └── delegates to active SchedulingAlgorithm:
        FCFS      → earliest arrivalTime (non-preemptive if running)
        RR        → first in queue if quantum expired
        SPN       → shortest burstTime (non-preemptive)
        SRT       → shortest remainingTime (preemptive)
        HRRN      → highest (wait+burst)/burst (non-preemptive)
        Priority  → lowest priority number (non-preemptive)

  if next == null or next == current → return (no switch)

  if current != null (preemption needed):
    stateManager.preemptToReady(current)    RUNNING → READY
    fireStateChanged(current, RUNNING, READY)

  statisticsManager.onProcessFirstRun(next, tick)  [sets responseTime]
  next.setStarted(true)

  if policy == ROUND_ROBIN:
    next.setQuantumRemaining(policyManager.getRrQuantum())

  stateManager.setRunning(next)        READY → RUNNING
  dispatcher.dispatch(next, cpu)
    └── cpu.loadProcess(next)
        next.setState(RUNNING)
        next.setCpuId(cpu.getId())
        contextSwitches++
  fireStateChanged(next, READY, RUNNING)
```

---

## Phase F: CPU Cycle Execution (Stage 5)

```
CycleEvent event = cpu.executeCycle()

CPU.executeCycle():
  if currentProcess == null → return CycleEvent.idle()
  
  instrs = currentProcess.getInstructions()
  regs   = currentProcess.getRegisters()
  pc     = regs.getPc()
  
  if pc >= instrs.size() → return CycleEvent.processFinished()

  inst = instrs.get(pc)

  if cyclesLeftForCurrentInstruction == 0:    [new instruction]
    cyclesLeftForCurrentInstruction = inst.getWeight()
    regs.setIr(pc)
  
  busyCycles++
  cyclesLeftForCurrentInstruction--

  if cyclesLeftForCurrentInstruction > 0:     [still busy with this instruction]
    return CycleEvent.normal()

  return applyInstruction(inst, regs)         [effect applied on final tick]
```

### Instruction Effects (applyInstruction)

| Instruction | Effect on registers | Return |
|-------------|--------------------|----|
| `LOAD reg` | `AC = reg` | `NORMAL` |
| `STORE reg` | `reg = AC` | `NORMAL` |
| `MOV dst, src\|val\|str` | `dst = src` or `DX = string` | `NORMAL` |
| `ADD reg` | `AC = AC + reg` | `NORMAL` |
| `SUB reg` | `AC = AC − reg` | `NORMAL` |
| `INC [reg]` | `reg++ or AC++` | `NORMAL` |
| `DEC [reg]` | `reg-- or AC--` | `NORMAL` |
| `SWAP r1,r2` | Exchanges values | `NORMAL` |
| `CMP r1,r2` | Sets `zeroFlag = (r1==r2)` | `NORMAL` |
| `JE offset` | `PC = PC+1+offset if zeroFlag` | `NORMAL` |
| `JNE offset` | `PC = PC+1+offset if !zeroFlag` | `NORMAL` |
| `JMP offset` | `PC = PC+1+offset` | `NORMAL` |
| `PARAM v1[,v2[,v3]]` | Pushes values to stack | `NORMAL` or `ERROR` if overflow |
| `PUSH reg` | Pushes reg value to stack | `NORMAL` or `ERROR` if overflow |
| `POP reg` | Pops stack into reg | `NORMAL` or `ERROR` if underflow |
| `INT_20H` | PC advanced | `PROCESS_FINISHED` |
| `INT_10H` | PC advanced | `SCREEN_OUTPUT(DX text)` |
| `INT_09H` | PC NOT advanced (advanced later by IOManager) | `KEYBOARD_INPUT` |
| `INT_21H` | PC advanced; reads AH, DX | `FILE_OPERATION(ah, filename)` |

---

## Phase G: CycleEvent Handling (Stage 6)

```
Kernel.handleCycleEvent(event, process, tick)

switch(event.getResult()):

  case NORMAL, IDLE:
    no action

  case PROCESS_FINISHED:
    cpu.releaseProcess()
    stateManager.terminate(process)          RUNNING → TERMINATED
    statisticsManager.onProcessFinished(process, tick)
    memoryManager.free(process)              RAM cleared; block freed
    fireProcessFinished(process)

  case SCREEN_OUTPUT:
    fireScreenOutput(event.getScreenText())  → GUI event log

  case KEYBOARD_INPUT:
    cpu.releaseProcess()
    stateManager.blockOnIO(process)          RUNNING → BLOCKED
    ioManager.submitKeyboardRequest(process) [duration = MAX_VALUE, never auto-completes]
    fireStateChanged(process, RUNNING, BLOCKED)
    waitingForKeyboard = process
    fireKeyboardInputRequired(process)       → GUI shows keyboard input row; pauses auto-run
    [executeTick() returns early on subsequent calls until provideKeyboardInput() called]

  case FILE_OPERATION:
    cpu.releaseProcess()
    stateManager.blockOnIO(process)          RUNNING → BLOCKED
    ioManager.submitFileRequest(process, ah, filename)
      └── buildFileRequest(subcode, filename, disk.serviceTime=5)
          Executes on Disk immediately (synchronous):
            0x3C (60) → disk.createFile(filename)
            0x3D (61) → disk.openFile(filename)
            0x40 (64) → disk.writeFile(filename, AL)
            0x4D (77) → disk.readFile(filename)
            0x41 (65) → disk.deleteFile(filename)
      └── waitingProcesses.put(pid, process)
    fireStateChanged(process, RUNNING, BLOCKED)
    [process unblocked after 5 ticks when IOManager.tick() detects completion]

  case ERROR:
    fireExecutionError(process, event.getErrorMessage())
    cpu.releaseProcess()
    stateManager.terminate(process)          RUNNING → TERMINATED
    statisticsManager.onProcessFinished(process, tick)
    memoryManager.free(process)
```

---

## Phase H: Round-Robin Quantum Check (Stage 7)

```
if policy == ROUND_ROBIN and running != null:
  running.decrementQuantumRemaining()
  if quantumRemaining <= 0 and event.result == NORMAL:
    interruptManager.raise(Interrupt(QUANTUM_EXPIRED, running))
    handleQuantumExpiry(running):
      cpu.releaseProcess()
      stateManager.preemptToReady(running)   RUNNING → READY
      fireStateChanged(running, RUNNING, READY)
```

---

## Phase I: Statistics Update (Stage 8)

```
statisticsManager.setContextSwitches(dispatcher.getContextSwitches())
statisticsManager.onTick(tick)
  totalTicks++
  if cpu.isIdle(): idleTicks++
  for each PCB in readyQueue:
    pcb.incrementWaitingTime()

// 8b: Refresh BCP summaries in OS area (cells 0–19)
for each PCB in queueManager.getAllLive():
  memoryManager.updateBcpOsArea(p)
if currentProcess != null:
  memoryManager.updateBcpOsArea(currentProcess)
```

---

## Phase J: Listener Notification and Termination Check (Stages 9–10)

```
fireTickCompleted(tick, cpu.getCurrentProcess())
  → SimuladorController.onTickCompleted(tick, running)
      ├── ctrl.refreshQueues()
      ├── ctrl.refreshMemory()
      ├── ctrl.refreshDisk()
      ├── ctrl.refreshStats()
      ├── ctrl.refreshCpu(running)
      └── currentTick.set(tick)   [bound to UI label]

if processManager.allFinished() and cpu.isIdle():
  halted = true
  fireAllFinished()
    → SimuladorController.onAllProcessesFinished(statisticsManager)
        logs final statistics table
        finished.set(true)
```

---

## Phase K: Keyboard Input Completion Flow

```
[User types value in tfKeyboard and clicks btnKeyboard]

SimuladorApp button handler:
  ctrl.provideKeyboardInput(int value)
    └── kernel.provideKeyboardInput(waitingForKeyboard, value)
          └── ioManager.completeKeyboardRequest(process, value)
                process.getRegisters().setDx(value)
                process.getRegisters().setPc(pc + 1)   ← advance past INT 09H
                process.setCurrentIORequest(null)
          stateManager.unblockToReady(process)         BLOCKED → READY
          fireStateChanged(process, BLOCKED, READY)
          waitingForKeyboard = null
          [auto-run resumes if it was running before]
```

---

## Complete Process Lifecycle State Machine

```
                    ┌─────────────────────────────────────┐
                    │                 NEW                  │
                    │  (pcb created, in newQueue)          │
                    └──────────────┬──────────────────────┘
                                   │ memory allocated + arrival <= tick
                                   ▼
                    ┌─────────────────────────────────────┐
                 ┌─►│                READY                 │◄──────────┐
                 │  │  (in readyQueue, waiting for CPU)    │           │
                 │  └──────────────┬──────────────────────┘           │
                 │                 │ scheduler selects                 │
                 │                 ▼                                   │
                 │  ┌─────────────────────────────────────┐           │
                 │  │              RUNNING                  │           │
                 │  │  (on CPU, executing instructions)    │           │
                 │  └──┬──────┬──────────┬────────────────┘           │
                 │     │      │          │                             │
              preempt  │   INT 09H/21H  INT 20H /           I/O done   │
              /quantum │   issued       last instr           (tick)    │
                 │     │      │          │                             │
                 │     │      ▼          ▼                             │
                 │     │  ┌────────┐  ┌──────────┐                    │
                 │     │  │BLOCKED │  │TERMINATED│                    │
                 └─────┘  │(I/O    │  │          │                    │
                           │wait)  │  └──────────┘                    │
                           └───────┴────────────────────────────────── ┘
```

---

## INT 21H File Operation Subcodes

| AH (decimal) | AH (hex) | Operation |
|-------------|----------|-----------|
| 60 | 0x3C | CREATE file |
| 61 | 0x3D | OPEN file |
| 64 | 0x40 | WRITE file (AL = data byte) |
| 77 | 0x4D | READ file |
| 65 | 0x41 | DELETE file |

All file operations **block the process** for 5 ticks (disk service time).  
Screen output (INT 10H) does **not** block; it fires `SCREEN_OUTPUT` and the process continues immediately.

---

## Sequence: Full Normal Execution

```
User           SimuladorApp    SimuladorController    Kernel        CPU/Subsystems
 |                  |                  |                |                  |
 |--load file-----> |                  |                |                  |
 |                  |--loadFiles()---> |                |                  |
 |                  |                  |--loadProgram-->|                  |
 |                  |                  |                |--Assembler.parse |
 |                  |                  |                |--new PCB         |
 |                  |                  |                |--submit PCB      |
 |                  |                  |                |--admitWaiting--> |
 |                  |                  |                |  MemoryManager   |
 |                  |  <--refreshUI--- |                |                  |
 |                  |                  |                |                  |
 |--step/auto-----> |                  |                |                  |
 |                  |--step()--------> |                |                  |
 |                  |                  |--executeTick-->|                  |
 |                  |                  |                |--clock.tick()    |
 |                  |                  |                |--loadArriving()  |
 |                  |                  |                |--ioManager.tick()|
 |                  |                  |                |--scheduleDispatch|
 |                  |                  |                |--cpu.execute()-->|
 |                  |                  |                |<--CycleEvent-----|
 |                  |                  |                |--handleEvent()   |
 |                  |                  |                |--stats.onTick()  |
 |                  |                  |                |--fireTickDone--> |
 |                  |  <--onTick---    |  <--onTick---  |                  |
 |                  |  refreshUI       |                |                  |
```

---

## Sequence: INT 09H Keyboard Interrupt

```
CPU executes INT 09H
  → CycleEvent.KEYBOARD_INPUT returned
  → Kernel.handleCycleEvent:
      cpu.releaseProcess()
      stateManager.blockOnIO(process)      → BLOCKED
      ioManager.submitKeyboardRequest()     [duration = MAX_VALUE]
      waitingForKeyboard = process
      fireKeyboardInputRequired(process)
         → controller.onKeyboardInputRequired()
              waitingKeyboard.set(true)
              autoTimeline.pause()          [if auto was running]
              show keyboard input row in GUI

[User types a value 0-255 in tfKeyboard, clicks "Enviar"]

controller.provideKeyboardInput(value)
  kernel.provideKeyboardInput(process, value)
    ioManager.completeKeyboardRequest(process, value)
      regs.setDx(value)
      regs.setPc(pc + 1)    ← advance past INT 09H
    stateManager.unblockToReady(process)   → READY
    waitingForKeyboard = null
  [auto-run resumes if kbInterruptedAuto == true]
```

---

## Sequence: INT 21H File Operation

```
CPU executes INT 21H (AH=60, DX="myfile.txt")
  → CycleEvent.FILE_OPERATION(subcode=60, filename="myfile.txt") returned
  → Kernel.handleCycleEvent:
      cpu.releaseProcess()
      stateManager.blockOnIO(process)       → BLOCKED
      ioManager.submitFileRequest(process, 60, "myfile.txt")
        disk.createFile("myfile.txt")        [synchronous]
        IORequest req = new IORequest(duration=5)
        device.disk.assign(req)
        waitingProcesses.put(pid, process)

[5 ticks later]
  ioManager.tick():
    disk device tick()
    disk.checkComplete() → true
    → process added to unblocked list

  Kernel receives unblocked list:
    stateManager.unblockToReady(process)   → READY
    interruptManager.raise(IO_FINISHED)
    fireStateChanged()
```
