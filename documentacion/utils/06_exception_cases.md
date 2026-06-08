# 06 — Exception and Error Cases

---

## 1. Parse-Time Errors (Assembler)

These errors are detected during `Assembler.parse(File)` before any simulation begins.  
All throw a checked `Exception` with a human-readable message including the source line number.

### 1.1 — Null or Non-Existent File

| When | `file == null` or `!file.exists()` |
|------|------------------------------------|
| Where | `Assembler.parse()` entry guard |
| Exception | `Exception("File does not exist: <path>")` |
| Handling | Caught in `SimuladorController.loadFiles()`; message added to error list; shown in event log |
| Effect | PCB never created; other files continue loading |

---

### 1.2 — Unknown Opcode

| When | Line contains a token not matching any known instruction (e.g., `HALTT`) |
|------|-------------------------------------------------------------------------|
| Where | `Assembler.parseLine()` default case |
| Exception | `Exception("[Line N] Unknown instruction: 'HALTT'")` |
| Demo file | `test_err_badasm.asm` line 11 |
| Effect | Entire file rejected; no PCB created |

---

### 1.3 — Missing or Wrong Operands

| When | Instruction has wrong number or type of operands |
|------|--------------------------------------------------|
| Examples | `MOV` with one operand; `ADD` with non-register; `SWAP` with integer instead of register |
| Where | `splitOperands()`, `requireRegister()`, `requireOneRegister()` |
| Exception | `Exception("[Line N] <description>. Expected: <syntax>")` |
| Demo file | `test_err_badoperands.asm` |
| Effect | Entire file rejected |

---

### 1.4 — Invalid Register Name

| When | Operand is not in `{AC, AX, BX, CX, DX, AH, AL}` |
|------|----------------------------------------------------|
| Where | `requireRegister()` |
| Exception | `Exception("[Line N] Invalid register 'X'. Valid registers: AC AX BX CX DX AH AL")` |
| Effect | Entire file rejected |

---

### 1.5 — Unknown Interrupt Code

| When | `INT` with code other than `20H`, `10H`, `09H`, `21H` |
|------|---------------------------------------------------------|
| Where | `Assembler.parseLine()` INT case |
| Exception | `Exception("[Line N] Unknown interrupt code 'XXH'. Valid: 20H, 10H, 09H, 21H.")` |
| Effect | Entire file rejected |

---

### 1.6 — String Literal on Wrong Register

| When | `MOV AX, somestring` (string literal assigned to non-DX register) |
|------|--------------------------------------------------------------------|
| Where | `Assembler.parseLine()` MOV case |
| Exception | `Exception("[Line N] String literals may only be assigned to DX.")` |
| Effect | Entire file rejected |

---

### 1.7 — Empty File

| When | File contains no valid instructions after stripping comments/blanks |
|------|--------------------------------------------------------------------|
| Where | After read loop in `Assembler.parse()` |
| Exception | `Exception("El archivo está vacío o no contiene instrucciones válidas.")` |
| Effect | Entire file rejected |

---

### 1.8 — Too Many Instructions (> 80)

| When | Parsed instruction count exceeds `AssemblerConfig.maxInstructions` (default 80; 0 = no limit) |
|------|-------------------------------------|
| Where | After read loop in `Assembler.parse()` |
| Exception | `Exception("El programa excede el límite de N instrucciones configurado en assembler-config.json.")` |
| Effect | Entire file rejected |

---

## 2. Memory Errors

### 2.1 — Insufficient RAM for Process

| When | `MemoryManager.allocate()` cannot find a contiguous block of `pcb.getMemoryRequired()` words |
|------|-----------------------------------------------------------------------------------------------|
| Where | `MemoryManager.findFreeBlock()` returns -1 |
| Return value | `false` from `allocate()` |
| Handling | `ProcessManager.loadArrivingProcesses()` skips that PCB; it remains in `newQueue` |
| Visible effect | Process stays in NEW state; not shown in READY table |
| Recovery | When another process terminates and frees memory, next tick's admission pass may succeed |

---

### 2.2 — RAM Too Small at Construction

| When | `new RAM(size)` called with `size ≤ 20` (OS_RESERVED) |
|------|---------------------------------------------------------|
| Where | `RAM` constructor |
| Exception | `IllegalArgumentException("RAM size must be greater than OS_RESERVED (20).")` |
| Handling | Program fails to start; caught by default exception handler |

---

### 2.3 — Disk Too Small at Construction

| When | `new Disk(size)` with `size ≤ 10` (INDEX_SIZE) |
|------|------------------------------------------------|
| Where | `Disk` constructor |
| Exception | `IllegalArgumentException("Disk size must be > 10")` |
| Handling | Program fails to start |

---

### 2.4 — Invalid RAM Address Access

| When | `ram.read(addr)` or `ram.write(addr, val)` with `addr < 0` or `addr >= size` |
|------|-------------------------------------------------------------------------------|
| Where | `RAM.checkBounds()` |
| Exception | `IndexOutOfBoundsException("Invalid RAM address: <addr>. Size: <size>")` |
| Note | Should not occur in normal operation; indicates internal logic bug |

---

## 3. Runtime CPU Errors

### 3.1 — Stack Overflow (PUSH / PARAM)

| When | `PUSH reg` or `PARAM` when stack is already at capacity (5 entries) |
|------|---------------------------------------------------------------------|
| Where | `RegisterSet.push()` → throws `StackOverflowError` |
| Detected in | `CPU.applyInstruction()` case PUSH / PARAM |
| CycleEvent returned | `CycleEvent.error("Stack overflow in PUSH at line N")` |
| Kernel handling | `handleCycleEvent(ERROR)`: `fireExecutionError()` + terminate process + free memory |
| Visible effect | Event log: `[ERROR] Proceso 'name': Stack overflow in PUSH at line N`; process moves to TERMINATED |
| Demo file | `test_err_stack.asm` (6th PUSH on 5-slot stack) |

---

### 3.2 — Stack Underflow (POP)

| When | `POP reg` when stack is empty |
|------|-------------------------------|
| Where | `RegisterSet.pop()` → throws `RuntimeException("Stack underflow – stack is empty")` |
| Detected in | `CPU.applyInstruction()` case POP |
| CycleEvent returned | `CycleEvent.error("Stack underflow in POP at line N")` |
| Kernel handling | Same as stack overflow |
| Demo file | `test_err_stackoverflow.asm` |

---

### 3.3 — Unhandled Instruction Type

| When | `CPU.applyInstruction()` encounters an `InstructionType` not in its switch |
|------|----------------------------------------------------------------------------|
| Where | Default case in `applyInstruction()` |
| CycleEvent returned | `CycleEvent.error("Unhandled instruction type: <type>")` |
| Note | Should not occur; all InstructionType values are handled |

---

### 3.4 — Program Counter Out of Bounds

| When | `pc < 0` or `pc >= instrs.size()` at start of `executeCycle()` |
|------|----------------------------------------------------------------|
| Where | `CPU.executeCycle()` guard |
| CycleEvent returned | `CycleEvent.processFinished()` |
| Effect | Process terminates normally (treated as natural end) |

---

## 4. Process State Transition Errors

### 4.1 — Invalid State Transition

| When | `StateManager.guardTransition()` called with a PCB in the wrong state |
|------|-----------------------------------------------------------------------|
| Where | `StateManager.admitToReady()` (expects NEW state) |
| Exception | `IllegalStateException("Process <pid> must be in <expected> state but is in <actual>")` |
| Note | Only `admitToReady()` uses `guardTransition()`; other transitions are permissive |

---

### 4.2 — Submit Non-NEW Process

| When | `ProcessManager.submit()` called with a PCB not in NEW state |
|------|--------------------------------------------------------------|
| Where | `ProcessManager.submit()` |
| Exception | `IllegalArgumentException("Only NEW processes may be submitted. PID=<pid>")` |

---

## 5. Interrupt Scenarios

### 5.1 — Quantum Expiry (ROUND_ROBIN)

| Trigger | `process.quantumRemaining <= 0` at end of tick while policy == ROUND_ROBIN |
|---------|----------------------------------------------------------------------------|
| Flow | `interruptManager.raise(QUANTUM_EXPIRED)` → `handleQuantumExpiry()` → `cpu.releaseProcess()` → `stateManager.preemptToReady()` → READY |
| Visible | Event log: `[SCHED] Quantum expirado para 'name'`; process appears back in READY queue |

---

### 5.2 — I/O Finished Interrupt

| Trigger | `ioManager.tick()` returns a non-empty list of unblocked processes |
|---------|-------------------------------------------------------------------|
| Flow | `interruptManager.raise(IO_FINISHED)` → `stateManager.unblockToReady()` → READY |
| Visible | Event log: `[I/O] 'name' desbloqueado`; process appears in READY queue |

---

### 5.3 — INT 09H Keyboard Block

| Trigger | Process executes `INT 09H` |
|---------|---------------------------|
| Flow | CPU → `KEYBOARD_INPUT` event → Kernel blocks process → `waitingForKeyboard` set → GUI shows keyboard row |
| Block | All `executeTick()` calls are no-ops while `waitingForKeyboard != null` |
| Resume | User provides value → `provideKeyboardInput()` → BLOCKED → READY |
| Edge case | If user never provides input, simulation is permanently paused |

---

### 5.4 — Simultaneous Multiple File Operations

| Scenario | Process P1 is blocked on disk (5 ticks); process P2 also issues INT 21H |
|----------|-------------------------------------------------------------------------|
| Issue | IOManager has only one `disk` Device instance; it can hold only one active `IORequest` |
| Current behavior | The disk Device's `currentRequest` gets overwritten when P2's request is assigned; P1's request may be lost |
| Note | This is a known limitation; in practice multi-process disk contention is possible but the disk device object only tracks the last assigned request. The `waitingProcesses` map tracks all blocked processes; the device timer is the bottleneck. |

---

## 6. Configuration Errors

### 6.1 — Missing or Malformed memory-config.json

| When | File absent from classpath, or JSON values cannot be parsed |
|------|-------------------------------------------------------------|
| Where | `MemoryConfig.load()` |
| Handling | Silent fallback to defaults: `RAM=512`, `VMem=64`, `Disk=512` |
| Visible | No error; simulation starts with default sizes |

---

### 6.2 — RAM/Disk Size Below Minimum

| When | JSON specifies `ramSize ≤ 20` or `diskSize ≤ 10` |
|------|--------------------------------------------------|
| Where | `MemoryConfig` constructor guards with `Math.max()` |
| Handling | Value clamped to minimum (RAM=21, Disk=11) silently |

---

## 7. I/O Errors

### 7.1 — File Does Not Exist on READ/OPEN/DELETE

| When | INT 21H with subcode READ (77), OPEN (61), or DELETE (65) for non-existent filename |
|------|--------------------------------------------------------------------------------------|
| Where | `Disk.openFile()`, `Disk.readFile()`, `Disk.deleteFile()` |
| Return | `false` from `openFile()`/`deleteFile()`, `null` from `readFile()` |
| Handling | IORequest is still created; process still blocks for 5 ticks; operation effectively a no-op |
| Note | No error event fired; process resumes normally after the I/O delay |

---

### 7.2 — Duplicate File CREATE

| When | INT 21H CREATE for a filename already in disk directory |
|------|--------------------------------------------------------|
| Where | `Disk.createFile()` |
| Return | `false` |
| Handling | Same as above — no-op; process blocks then resumes |

---

### 7.3 — Disk Full on CREATE

| When | `nextDataAddress >= size` (disk data area exhausted) |
|------|----------------------------------------------------|
| Where | `Disk.createFile()` |
| Return | `false` |
| Handling | No-op; process blocks then resumes |

---

## 8. JavaFX / UI Edge Cases

### 8.1 — Kernel Not Booted

| When | `executeTick()` called before `boot()` |
|------|---------------------------------------|
| Guard | `if (!booted || halted) return;` |
| Effect | Silent no-op |

### 8.2 — Execution After Halt

| When | `executeTick()` called after all processes finished (`halted=true`) |
|------|---------------------------------------------------------------------|
| Guard | `if (!booted || halted) return;` |
| Effect | Silent no-op; user must click **Reiniciar** to restart |
| Recovery | `ctrl.loadFiles()` calls `kernel.clearHalt()` automatically |

### 8.3 — Tick While Waiting for Keyboard

| When | `executeTick()` called while `waitingForKeyboard != null` |
|------|----------------------------------------------------------|
| Guard | `if (waitingForKeyboard != null) return;` |
| Effect | Silent no-op; auto-run Timeline fires but does nothing |

### 8.4 — Reset During Auto-Run

| When | User clicks **↺ Reiniciar** while auto Timeline is running |
|------|-----------------------------------------------------------|
| Flow | `ctrl.reset()` → `stopAuto()` first (Timeline stopped) → then full reset |
| Effect | Clean state; no race condition |

### 8.5 — Loading New Files After Simulation Completes

| When | Simulation finished (`halted=true`); user loads more files without resetting |
|------|-----------------------------------------------------------------------------|
| Flow | `loadFiles()` calls `kernel.clearHalt()` → simulation can resume |
| Effect | New processes added on top of existing terminated queue |
