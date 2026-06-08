# 04 — Flujo de Ejecución

---

## 1. Visión General del Pipeline

El simulador ejecuta un pipeline cíclico denominado **tick**. Cada tick representa un ciclo de reloj del procesador simulado. El pipeline completo de un tick se compone de 10 etapas ejecutadas de forma secuencial dentro de `Kernel.executeTick()`.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         KERNEL.executeTick()                                │
│                                                                             │
│  GUARDA DE ENTRADA                                                          │
│  if (!booted || halted || waitingForKeyboard != null) return;               │
│                                                                             │
│  ① clock.tick()                → incrementa contador global de ticks       │
│  ② processManager.loadArriving()→ NEW → READY (si RAM disponible)          │
│  ③ ioManager.tick()            → avanza E/S; desbloquea completados        │
│  ④ scheduleAndDispatch()       → selecciona proceso; carga en CPU          │
│  ⑤ cpu.executeCycle()          → ejecuta instrucción; genera CycleEvent    │
│  ⑥ handleCycleEvent()          → reacciona al resultado del ciclo          │
│  ⑦ quantum check (Round Robin) → si expiró: RUNNING → READY               │
│  ⑧ statisticsManager.onTick()  → actualiza métricas + BCP en OS area      │
│  ⑨ fireTickCompleted()         → notifica listeners (actualiza UI)         │
│  ⑩ allFinished() check         → si todo terminó: halted=true + fireAllFinished()
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Fase A — Carga y Análisis del Archivo ASM

```
Usuario selecciona archivo(s) .asm
          │
          ▼
SimuladorController.loadFiles()
          │
          ├──► Para cada archivo seleccionado:
          │         │
          │         ▼
          │    Assembler.parse(File file)
          │         │
          │         ├─ Verifica: file != null && file.exists()
          │         │   └── Si no: lanza Exception("File does not exist: <path>")
          │         │
          │         ├─ Lee cada línea del archivo
          │         │   ├─ Descarta líneas vacías y comentarios (inician con ';')
          │         │   └─ Para cada línea con contenido:
          │         │         │
          │         │         ▼
          │         │    parseLine(línea, numLínea)
          │         │         │
          │         │         ├─ Tokeniza: primer token = opcode
          │         │         ├─ Busca en switch-case por opcode
          │         │         ├─ Valida operandos (registros, enteros, literales)
          │         │         └─ Crea Instruction(type, op1, op2, literal, original,
          │         │                              weight, lineNumber)
          │         │
          │         ├─ Verifica: instrucciones > 0
          │         │   └── Si no: lanza Exception("El archivo está vacío...")
          │         │
          │         ├─ Verifica: instrucciones <= 80
          │         │   └── Si no: lanza Exception("El programa excede el límite...")
          │         │
          │         └─ Retorna List<Instruction>
          │
          ├──► Si parse() lanzó Exception:
          │       └─ Agrega mensaje a lista de errores; continúa con siguiente archivo
          │
          └──► Si parse() exitoso:
                    │
                    ▼
              Crea PCB:
                pid          = AtomicInteger.getAndIncrement()
                name         = filename sin extensión
                state        = NEW
                arrivalTime  = 0 (o tick actual)
                burstTime    = Σ instruction.weight para toda la lista
                remainingTime= burstTime
                priority     = 0 (valor por defecto)
                instructions = List<Instruction> del parser
                memoryRequired = instructions.size()
                registers    = new RegisterSet() (pc=0, todos en 0)
                    │
                    ▼
              kernel.loadProcess(PCB)
                    │
                    ▼
              processManager.submit(PCB)
                    │
                    ▼
              queueManager.admitToNew(PCB)
              (PCB queda en cola NEW)
```

---

## 3. Fase B — Admisión a Memoria y Cola READY

Esta fase ocurre en la **etapa ②** de cada tick: `processManager.loadArrivingProcesses(tick)`.

```
Para cada PCB en newQueue:
          │
          ├─ Verifica: PCB.arrivalTime <= tick
          │   └── Si no: salta este proceso (aún no ha llegado)
          │
          ├─ Verifica: procesos activos < MAX_CONCURRENT_PROCESSES (5)
          │   └── Si no: salta (demasiados procesos simultáneos)
          │
          ├─ Intenta asignar memoria: memoryManager.allocate(PCB)
          │   ├─ Primer ajuste: busca primer bloque libre con tamaño >= memoryRequired
          │   ├─ Si encuentra bloque:
          │   │     PCB.memoryBase = block.base
          │   │     PCB.memoryLimit = block.base + block.size - 1
          │   │     block.free = false
          │   │     block.pid = PCB.pid
          │   │     Escribe instrucciones del proceso en RAM[base..base+n-1]
          │   │     Retorna true
          │   └─ Si no encuentra: retorna false (proceso permanece en NEW)
          │
          └─ Si allocate() exitoso:
                stateManager.admitToReady(PCB)
                    └─ PCB.state = READY
                       queueManager.moveToReady(PCB)
                       fireProcessStateChanged(PCB, NEW, READY)
```

---

## 4. Fase C — Avance de E/S (Etapa ③)

```
ioManager.tick()
          │
          ├─ keyboard.tick()  → descuenta (pero latencia=MAX_VALUE: nunca completa solo)
          ├─ screen.tick()    → descuenta (latencia=1)
          └─ disk.tick()      → descuenta (latencia=5)
                    │
                    ▼
          Para cada dispositivo donde checkComplete() == true:
                    │
                    ├─ Obtiene PCB desde waitingProcesses.get(device.assignedPid)
                    ├─ device.release()
                    ├─ waitingProcesses.remove(PCB.pid)
                    └─ Agrega PCB a lista de desbloqueados
                    │
                    ▼
          Kernel recibe List<PCB> desbloqueados:
          Para cada PCB desbloqueado:
                stateManager.unblockToReady(PCB)
                    └─ PCB.state = READY
                       queueManager.moveToReady(PCB)
                       fireProcessStateChanged(PCB, BLOCKED, READY)
```

---

## 5. Fase D — Planificación y Despacho (Etapa ④)

```
Kernel.scheduleAndDispatch(tick)
          │
          ├─ Obtiene lista READY: queueManager.getReadyQueue()
          │
          ├─ Invoca: schedulingPolicyManager.selectNext(readyQueue, cpu.getCurrentProcess())
          │           └─ algoritmo activo devuelve el proceso seleccionado (o null si vacía)
          │
          ├─ Si selectedProcess == null:
          │     CPU permanece ociosa (o continúa con proceso actual si lo hay)
          │
          ├─ Si CPU está ociosa Y selectedProcess != null:
          │     dispatcher.dispatch(selectedProcess, cpu)
          │         ├─ cpu.loadProcess(selectedProcess)
          │         │     └─ cpu.currentProcess = selectedProcess
          │         │        cpu.cycleCountdown = instrucción_actual.weight
          │         ├─ contextSwitches++
          │         ├─ stateManager.setRunning(selectedProcess)
          │         │     └─ selectedProcess.state = RUNNING
          │         │        queueManager.removeFromReady(selectedProcess)
          │         └─ Si es la primera vez: statisticsManager.onProcessFirstRun()
          │                                  selectedProcess.responseTime = tick - arrivalTime
          │
          └─ Si algoritmo es APROPIATIVO (SRT) y selectedProcess != currentRunning:
                dispatcher.preempt(cpu)
                    └─ cpu.releaseProcess()
                stateManager.preemptToReady(currentProcess)
                dispatcher.dispatch(selectedProcess, cpu)
```

---

## 6. Fase E — Ciclo de CPU (Etapa ⑤)

```
CPU.executeCycle()
          │
          ├─ Si cpu.currentProcess == null: retorna CycleEvent.idle()
          │
          ├─ Si pc >= instructions.size(): retorna CycleEvent.processFinished()
          │
          ├─ cycleCountdown--
          │
          ├─ Si cycleCountdown > 0: retorna CycleEvent.normal()  (instrucción en progreso)
          │
          └─ Si cycleCountdown == 0: (instrucción completa)
                    │
                    ▼
              Instruction instr = getCurrentInstruction()
              applyInstruction(instr)
                    │
                    ├─ MOV: dest_reg = src_reg o literal
                    ├─ LOAD: ac = RAM[vmm.translate(PCB, operand)]
                    ├─ STORE: RAM[vmm.translate(PCB, operand)] = ac
                    ├─ ADD: dest_reg += src_reg
                    ├─ SUB: dest_reg -= src_reg
                    ├─ INC: reg++
                    ├─ DEC: reg--
                    ├─ SWAP: intercambia dos registros
                    ├─ CMP: zeroFlag = (op1 == op2)
                    ├─ JE: si zeroFlag: pc = targetLabel (búsqueda lineal)
                    ├─ JNE: si !zeroFlag: pc = targetLabel
                    ├─ JMP: pc = targetLabel
                    ├─ PUSH: stack[++sp] = reg; lanza StackOverflowError si sp>=4
                    ├─ POP: reg = stack[sp--]; lanza RuntimeException si sp<0
                    ├─ PARAM: stack[++sp] = immediate; lanza StackOverflowError si sp>=4
                    ├─ INT 20H: retorna CycleEvent.processFinished()
                    ├─ INT 10H: retorna CycleEvent.screenOutput(ac o dx toString)
                    ├─ INT 09H: retorna CycleEvent.keyboardInput()
                    └─ INT 21H: ejecuta operación de disco (Disk.createFile/writeFile/etc.)
                                retorna CycleEvent.fileOperation(ah, dxString)
                    │
                    ▼
              pc++ (avanza al siguiente)
              cycleCountdown = siguiente_instrucción.weight
              retorna CycleEvent
```

---

## 7. Fase F — Manejo del Evento de Ciclo (Etapa ⑥)

```
Kernel.handleCycleEvent(CycleEvent ev, PCB process, long tick)
          │
          ├─ NORMAL:
          │     process.executedTime++
          │     process.remainingTime--
          │     process.quantumRemaining--
          │     Actualiza waitingTime de todos los procesos en READY
          │
          ├─ IDLE:
          │     No hay proceso activo; actualiza waitingTime de READY
          │
          ├─ SCREEN_OUTPUT:
          │     fireScreenOutput(process, ev.screenText)
          │     → UI muestra: "[PANTALLA] PID N 'name': <texto>"
          │     process.executedTime++; process.remainingTime--; etc.
          │
          ├─ KEYBOARD_INPUT:
          │     waitingForKeyboard = process
          │     ioManager.submitKeyboardRequest(process)
          │     stateManager.blockOnIO(process) → BLOCKED
          │     cpu.releaseProcess()
          │     fireKeyboardInputRequired(process)
          │     → UI pausa; muestra campo de entrada
          │
          ├─ FILE_OPERATION:
          │     ioManager.submitFileRequest(process, ev.fileSubcode, ev.filename)
          │     stateManager.blockOnIO(process) → BLOCKED
          │     cpu.releaseProcess()
          │     fireFileOperation(process, opName, ev.filename)
          │     → UI muestra: "[I/O] PID N → CREAR|LEER|... 'filename'"
          │
          ├─ PROCESS_FINISHED:
          │     cpu.releaseProcess()
          │     statisticsManager.onProcessFinished(process, tick)
          │     stateManager.terminate(process) → TERMINATED
          │     memoryManager.free(process)
          │     fireProcessStateChanged(process, RUNNING, TERMINATED)
          │
          └─ ERROR:
                cpu.releaseProcess()
                fireExecutionError(process, ev.errorMessage)
                statisticsManager.onProcessFinished(process, tick)
                stateManager.terminate(process) → TERMINATED
                memoryManager.free(process)
                → UI muestra: "[ERROR] PID N 'name': <mensaje>"
```

---

## 8. Fase G — Verificación de Quantum (Etapa ⑦, solo Round Robin)

```
Si política == ROUND_ROBIN Y proceso != null Y process.quantumRemaining <= 0:
          │
          ▼
Kernel.handleQuantumExpiry(process, tick)
          │
          ├─ interruptManager.raise(QUANTUM_EXPIRED, process, null, 0)
          ├─ cpu.releaseProcess()
          ├─ stateManager.preemptToReady(process) → READY
          ├─ process.quantumRemaining = roundRobinQuantum  (reinicia quantum)
          └─ fireProcessStateChanged(process, RUNNING, READY)
             → UI muestra: "[SCHED] Quantum expirado para 'name'"
```

---

## 9. Fase H — Estadísticas y Notificación (Etapas ⑧ y ⑨)

```
Etapa 8:
statisticsManager.onTick(tick)
    ├─ totalTicks++
    └─ Si CPU activa: activeTicks++

memoryManager.storeBcpInOsArea()
    └─ Escribe estado actual de cada proceso activo en celdas 0-19 de RAM

Etapa 9:
fireTickCompleted(tick)
    └─ Para cada KernelEventListener:
          listener.onTickCompleted(tick)
              └─ SimuladorController.onTickCompleted()
                    ├─ currentTickProp.set(tick)
                    ├─ Actualiza propiedades de CPU (registros, estado)
                    ├─ Actualiza ObservableLists de las 4 tablas de colas
                    ├─ Actualiza mapa de RAM y disco
                    └─ Actualiza estadísticas en el panel lateral
```

---

## 10. Fase I — Verificación de Finalización (Etapa ⑩)

```
Si allFinished() == true:
    ├─ halted = true
    ├─ fireAllFinished()
    │     └─ listener.onAllFinished()
    │           └─ UI muestra: "[SIM] Simulación completada. Todos los procesos finalizaron."
    │              autoTimeline.stop()
    └─ Todos los ticks futuros son ignorados por la guarda de entrada
```

---

## 11. Diagrama de Máquina de Estados del Proceso

```
                           submit()
           ┌─────────────────────────────────────┐
           │                                     │
           ▼                                     │
         [NEW]
           │
           │ admitToReady()
           │ (RAM disponible, tick >= arrivalTime)
           ▼
        [READY] ◄────────────────────────────────────────┐
           │          preemptToReady()                   │
           │          unblockToReady()                   │
           │                                             │
           │ dispatch()                                  │
           │ setRunning()                                │
           ▼                                             │
       [RUNNING]                                         │
           │                                             │
           ├──── INT 09H / INT 21H ──► [BLOCKED] ────────┘
           │     blockOnIO()            (E/S completada)
           │
           ├──── QUANTUM_EXPIRED ──────────────────────────┘
           │     preemptToReady()
           │
           ├──── INT 20H / PC >= límite ──► [TERMINATED]
           │     terminate()
           │
           └──── ERROR (stack overflow, etc.) ──► [TERMINATED]
                 terminate()

           [TERMINATED]
               (estado final; no hay transiciones salientes)
```

---

## 12. Flujo de Secuencia: Ejecución Normal

```
Usuario         SimuladorController    Kernel           CPU           SchedulingPolicyManager
  │                      │               │                │                    │
  │──── step() ─────────►│               │                │                    │
  │                      │──executeTick()►│                │                    │
  │                      │               │── clock.tick() │                    │
  │                      │               │── loadArriving()                    │
  │                      │               │── ioManager.tick()                  │
  │                      │               │── scheduleAndDispatch() ────────────►│
  │                      │               │◄── selectedProcess ─────────────────│
  │                      │               │── dispatch(PCB, CPU) ───────────────►│
  │                      │               │                │── loadProcess(PCB) │
  │                      │               │── executeCycle()───────────────────►│
  │                      │               │◄── CycleEvent(NORMAL) ──────────────│
  │                      │               │── handleCycleEvent(NORMAL)          │
  │                      │               │── statistics.onTick()               │
  │                      │               │── fireTickCompleted()               │
  │                      │◄──onTickCompleted()────────────│                    │
  │                      │── refreshUI() │                │                    │
  │◄── UI actualizada ───│               │                │                    │
```

---

## 13. Flujo de Secuencia: INT 09H (Entrada de Teclado)

```
CPU              Kernel           IOManager        SimuladorController     Usuario
 │                  │                 │                      │                │
 │──CycleEvent ─────►│                 │                      │                │
 │  KEYBOARD_INPUT   │                 │                      │                │
 │                  │──submitKeyboard()►│                      │                │
 │                  │                 │── keyboard.assign()   │                │
 │                  │                 │── waitingProcesses    │                │
 │                  │                 │   .put(pid, PCB)      │                │
 │                  │──blockOnIO(PCB)──►                       │                │
 │                  │   PCB→BLOCKED   │                      │                │
 │                  │──releaseProcess()►                       │                │
 │                  │──fireKeyboard()──────────────────────────►│                │
 │                  │                 │                      │── muestra campo │
 │                  │                 │                      │   de entrada    │
 │                  │                 │                      │◄── valor(n) ───│
 │                  │◄─ provideKeyboardInput(n) ─────────────│                │
 │                  │──completeKeyboard(PCB, n)──────────────►│                │
 │                  │                 │── ax = n             │                │
 │                  │                 │── keyboard.release() │                │
 │                  │                 │── remove(pid)        │                │
 │                  │──unblockToReady(PCB)──► PCB→READY      │                │
 │                  │── siguiente tick: PCB planificado normalmente             │
```

---

## 14. Flujo de Secuencia: INT 21H (Operación de Archivo)

```
CPU              Kernel           IOManager       Disk         SimuladorController
 │                  │                 │             │                   │
 │── CycleEvent ────►│                 │             │                   │
 │   FILE_OPERATION  │                 │             │                   │
 │   (ah=60, "arch") │                 │             │                   │
 │                  │── ejecutar op. en Disk ───────►│                   │
 │                  │   (ah=60: createFile("arch"))  │                   │
 │                  │◄── true/false ─────────────────│                   │
 │                  │── submitFileRequest(PCB) ──────►│                  │
 │                  │                 │── disk.assign(IORequest, pid)    │
 │                  │                 │── waitingProcesses.put(pid, PCB)  │
 │                  │── blockOnIO(PCB) → BLOCKED      │                   │
 │                  │── releaseProcess()              │                   │
 │                  │── fireFileOperation() ───────────────────────────── ►│
 │                  │                 │             │                   │── log evento
 │                  │       ... 5 ticks después ...  │                   │
 │                  │── ioManager.tick() retorna PCB │                   │
 │                  │── unblockToReady(PCB) → READY  │                   │
 │                  │── fireStateChanged() ──────────────────────────────►│
```

---

## 15. Subcódigos de INT 21H

| Valor de `ah` (dec) | Valor hex | Operación | Recurso afectado |
|---------------------|-----------|-----------|-----------------|
| 60 | 0x3C | `Disk.createFile(dxString)` | Crea nueva entrada en directorio |
| 61 | 0x3D | `Disk.openFile(dxString)` | Verifica existencia del archivo |
| 64 | 0x40 | `Disk.writeFile(dxString, al.toString())` | Escribe datos en el archivo |
| 77 | 0x4D | `Disk.readFile(dxString)` → guarda en `ac` | Lee contenido del archivo |
| 65 | 0x41 | `Disk.deleteFile(dxString)` | Elimina entrada del directorio |

---

## 16. Diagrama de Flujo: Ciclo Completo de un Proceso

```
[INICIO]
   │
   ▼
Usuario carga archivo .asm
   │
   ▼
Assembler.parse() → List<Instruction>
   │
   ├─── Error de sintaxis ──► Mostrar error; proceso NO creado
   │
   ▼
PCB creado → state=NEW → encolado en newQueue
   │
   ▼
Tick N: processManager.loadArrivingProcesses()
   │
   ├─── RAM insuficiente ──► Proceso permanece en NEW; reintentar en siguiente tick
   │
   ▼
RAM asignada → state=READY → encolado en readyQueue
   │
   ▼
Tick M: scheduler.selectNext() → proceso elegido
   │
   ▼
dispatcher.dispatch() → state=RUNNING → CPU.loadProcess()
   │
   ▼
CPU.executeCycle() → aplica instrucciones tick a tick
   │
   ├─── SCREEN_OUTPUT ──► Log UI; continúa ejecución
   │
   ├─── KEYBOARD_INPUT ──► state=BLOCKED; espera entrada; continúa cuando llega
   │
   ├─── FILE_OPERATION ──► state=BLOCKED; espera 5 ticks; state=READY al completar
   │
   ├─── QUANTUM_EXPIRED ──► state=READY; reinicia quantum; espera nuevo turno
   │
   ├─── ERROR ──► state=TERMINATED; libera RAM; muestra error en UI
   │
   └─── PROCESS_FINISHED ──► state=TERMINATED; libera RAM; registra estadísticas
               │
               ▼
        ¿Todos terminados?
               │
        Sí ────►─── halted=true → "Simulación completada"
               │
        No ────►─── Continuar pipeline
```
