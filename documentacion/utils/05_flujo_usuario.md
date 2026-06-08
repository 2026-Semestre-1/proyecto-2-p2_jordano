# 05 — Flujo de Interacción del Usuario

---

## 1. Inicio de la Aplicación

```
MainFrame.main(args)
    └──► SimuladorApp.main(args)
              └──► JavaFX Application.launch()
                        │
                        ▼
               SimuladorApp.start(Stage primaryStage)
                        │
                        ├── Crea SimuladorController
                        │     └── Instancia Kernel (con MemoryConfig cargado de memory-config.json)
                        │         RAM=700, VMem=80, Disco=1080
                        │         Política inicial: FCFS
                        │
                        ├── Construye Scene Graph:
                        │     ├── BorderPane raíz
                        │     │     ├── TOP: barra de herramientas
                        │     │     └── CENTER: SplitPane horizontal
                        │     │           ├── Izquierda: SplitPane vertical
                        │     │           │     ├── Panel BCP + registros CPU
                        │     │           │     └── Registro de eventos + teclado
                        │     │           ├── Centro: ScrollPane con 4 tablas de cola
                        │     │           └── Derecha: TabPane (RAM | Disco)
                        │
                        └── Muestra la ventana (1440 × 860 px)
```

---

## 2. Descripción Visual Detallada de la Interfaz

### 2.1 Barra de Herramientas (TOP)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  [Cargar archivos]  [Auto-todos]  [Por proceso]  [Paso]  [Detener]  [Reiniciar]  │
│  [Info]                                               Tick: 0    ● FCFS          │
└──────────────────────────────────────────────────────────────────────────────────┘
```

| Control | Tipo | Acción al hacer clic |
|---------|------|----------------------|
| `Cargar archivos` | Botón | Abre selector de archivos con filtro `*.asm`; permite selección múltiple |
| `Auto-todos` | Botón toggle | Inicia/detiene el `Timeline` de JavaFX (período 400 ms); avanza ticks automáticamente hasta que todos los procesos terminen o un proceso espere teclado |
| `Por proceso` | Botón | Ejecuta ticks continuamente hasta que un proceso adicional llega a estado `TERMINATED` |
| `Paso` | Botón | Ejecuta exactamente un tick del kernel |
| `Detener` | Botón | Detiene el `autoTimeline` si está activo |
| `Reiniciar` | Botón | Detiene la ejecución, llama `kernel.reset()`, limpia todas las colas y la UI |
| `Info` | Botón | Muestra estadísticas finales del sistema (utilización CPU, productividad, tiempos promedio) |
| Etiqueta `Tick: N` | Label | Vinculada a `currentTickProp`; se actualiza en cada `onTickCompleted` |
| Etiqueta `FCFS` | Label | Muestra la política de planificación activa del kernel |

### 2.2 Panel Izquierdo

El panel izquierdo contiene un `SplitPane` vertical con dos secciones:

**Sección superior — BCP activo y estado de CPU**
```
┌─────────────────────────────────────────┐
│  ● CPU: Activa  │  Proceso: nombre      │
│─────────────────────────────────────────│
│  PID:   [valor]   Estado: [estado]      │
│  PC:    [valor]   Ráfaga: [valor]       │
│  IR:    [valor]   Prioridad: [valor]    │
│─────────────────────────────────────────│
│  AC:  [val]  AX: [val]  BX: [val]      │
│  CX:  [val]  DX: [val]  AH: [val]      │
│  AL:  [val]                             │
└─────────────────────────────────────────┘
```
- El indicador circular (`Circle`) cambia de color: verde = CPU activa, gris = ociosa.
- Todos los valores están vinculados a `StringProperty` del controlador y se actualizan por evento.

**Sección inferior — Registro de eventos y teclado**
```
┌─────────────────────────────────────────┐
│  [Evento 1]                             │
│  [Evento 2]                             │
│  ...                                    │
│─────────────────────────────────────────│
│  Entrada teclado: [___________] [→]     │
└─────────────────────────────────────────┘
```
- El `ListView<String>` crece con prioridad `ALWAYS` dentro del panel.
- La fila de teclado se habilita/deshabilita según `waitingForKeyboard != null`.

### 2.3 Panel Central

```
┌──────────────────────────────────────────────┐  ▲
│  COLA NUEVA (NEW)                            │  │
│  PID │ Nombre │ Estado │ Prioridad │ Ráfaga  │  │
│  ──────────────────────────────────────────  │  │
│  ...                                         │  │
├──────────────────────────────────────────────┤  S
│  COLA LISTA (READY)                          │  C
│  PID │ Nombre │ Estado │ Prioridad │ Ráfaga  │  R
│  ...                                         │  O
├──────────────────────────────────────────────┤  L
│  COLA BLOQUEADA (BLOCKED)                    │  L
│  ...                                         │  │
├──────────────────────────────────────────────┤  │
│  COLA TERMINADA (TERMINATED)                 │  │
│  PID │ Nombre │ T.Inicio│ T.Fin │ T.Espera  │  ▼
└──────────────────────────────────────────────┘
```
- Las cuatro tablas (`TableView`) están apiladas verticalmente dentro de un `ScrollPane`.
- Cada tabla está vinculada a su `ObservableList<ProcessRow>` correspondiente.
- Se actualiza en cada `onTickCompleted` mediante `Platform.runLater()`.

### 2.4 Panel Derecho

```
┌─────────────────────────────────┐
│  [ RAM ]  [ Disco ]             │  ← TabPane
│─────────────────────────────────│
│  Dir │  Valor  │  Propietario   │
│  000 │  [val]  │  [pid/SO]      │
│  001 │  [val]  │  ...           │
│  ... │  ...    │  ...           │
└─────────────────────────────────┘
```
- Pestaña **RAM**: muestra todas las celdas de la RAM (700 entradas); las primeras 20 pertenecen al SO.
- Pestaña **Disco**: muestra el contenido del disco (1080 celdas) incluyendo el directorio en índices 0–9.

---

## 3. Escenario 1 — Carga de Archivos

```
Usuario
  │
  ├─ Clic en «Cargar archivos»
  │         │
  │         ▼
  │   FileChooser.showOpenMultipleDialog()
  │         │
  │         ├─ Usuario selecciona uno o varios .asm
  │         │         │
  │         │         ▼
  │   Para cada archivo seleccionado:
  │         │
  │         ├─ Assembler.parse(file)
  │         │     ├─ Éxito  →  List<Instruction> válida
  │         │     │             │
  │         │     │             ▼
  │         │     │   Crear PCB:
  │         │     │     pid  = auto-increment
  │         │     │     name = filename sin .asm
  │         │     │     burstTime = Σ instruction.weight
  │         │     │     memoryRequired = instructions.size()
  │         │     │     state = NEW
  │         │     │             │
  │         │     │             ▼
  │         │     │   kernel.loadProcess(PCB)
  │         │     │   → Log: "[LOAD] PID N 'nombre' cargado."
  │         │     │
  │         │     └─ Error → Log: "[ERROR] nombre.asm: <descripción con línea>"
  │         │               El archivo es descartado; continúa con el siguiente
  │         │
  │   Fin del bucle: la UI refresca la tabla NEW
  │
```

**Condiciones de validación al cargar**:
- El archivo no debe ser nulo ni inexistente.
- No debe estar vacío (al menos 1 instrucción válida).
- No debe superar `maxInstructions` (80 por defecto).
- Todos los opcodes, registros y operandos deben ser válidos.
- Los literales de cadena solo pueden asignarse al registro `DX`.

---

## 4. Escenario 2 — Ejecución Manual (Paso a Paso)

```
Usuario
  │
  └─ Clic en «Paso»  (mientras booted=true, halted=false, !waitingForKeyboard)
           │
           ▼
  SimuladorController.step()
           │
           ▼
  kernel.executeTick()  [ver 04_flujo_ejecucion.md para detalle completo]
           │
           ▼
  onTickCompleted(tick)  →  Platform.runLater():
           ├─ currentTickProp.set(tick)
           ├─ Actualiza regProp (AC, AX, BX...) desde proceso en CPU
           ├─ Actualiza cpuStatusProp / cpuProcessProp
           ├─ Recarga newQueueRows, readyQueueRows, blockedQueueRows, terminatedQueueRows
           ├─ Actualiza memoryRows (700 entradas) y diskRows
           └─ Actualiza propiedades de estadísticas globales
```

**Resultado observable en cada Paso**:
- El contador de tick incrementa en 1.
- Los procesos pueden avanzar entre colas según el pipeline del kernel.
- Los registros del proceso en CPU reflejan los cambios producidos por la instrucción ejecutada.
- Los cambios en RAM y disco se reflejan inmediatamente en el panel derecho.

---

## 5. Escenario 3 — Ejecución Automática

```
Usuario clic «Auto-todos»
           │
           ▼
  autoTimeline.play()  [período 400 ms]
           │
           ├─ Cada 400 ms: SimuladorController.step()  →  kernel.executeTick()
           │
           ├─ La simulación continúa hasta:
           │     (a) halted = true  →  autoTimeline.stop()
           │     (b) waitingForKeyboard != null  →  el kernel detiene ticks
           │     (c) Usuario clic «Detener»  →  autoTimeline.stop()
           │
           └─ Al finalizar: onAllFinished() muestra estadísticas completas
```

---

## 6. Escenario 4 — Ejecución por Proceso

```
Usuario clic «Por proceso»
           │
           ▼
  SimuladorController.startSingleProcess()
           │
           ├─ Guarda el conteo actual de terminados: n0 = terminatedQueueRows.size()
           │
           └─ Ejecuta ticks en bucle hasta:
                 terminatedQueueRows.size() > n0
                 O  waitingForKeyboard != null
                 O  halted = true
```

Útil para observar el ciclo de vida completo de un único proceso sin avanzar más allá de su terminación.

---

## 7. Escenario 5 — Interrupción de Teclado (INT 09H)

```
[Durante ejecución]
  CPU ejecuta INT 09H
           │
           ▼
  CycleEvent.keyboardInput()
           │
           ▼
  Kernel.handleCycleEvent(KEYBOARD_INPUT, process, tick)
           ├─ waitingForKeyboard = process
           ├─ ioManager.submitKeyboardRequest(process)
           ├─ stateManager.blockOnIO(process)  →  RUNNING → BLOCKED
           ├─ cpu.releaseProcess()
           └─ fireKeyboardInputRequired(process)
                        │
                        ▼
  SimuladorController.onKeyboardInputRequired(process)
           ├─ waitingForKeyboardProcess = process
           └─ Platform.runLater():
                 tfKeyboard.setDisable(false)
                 btnKeyboard.setDisable(false)
                 Log: "[KB] Proceso PID N espera entrada de teclado"

[Usuario escribe valor y presiona →]
           │
           ▼
  SimuladorController.provideKeyboardInput(intValue)
           ├─ kernel.provideKeyboardInput(intValue)
           │     ├─ ioManager.completeKeyboardRequest(process, intValue)
           │     │     └─ process.registers.ax = intValue
           │     ├─ waitingForKeyboard = null
           │     └─ stateManager.unblockToReady(process)  →  BLOCKED → READY
           │
           └─ UI deshabilita el campo de teclado
```

---

## 8. Escenario 6 — Operación de Archivo (INT 21H)

```
CPU ejecuta INT 21H  (AH = subcódigo de operación, DX = nombre de archivo)
           │
           ▼
  CPU.applyInstruction()
           ├─ Subcódigo 1 (CREATE)  →  disk.createFile(dxString)
           ├─ Subcódigo 2 (OPEN)    →  disk.openFile(dxString)
           ├─ Subcódigo 3 (WRITE)   →  disk.writeFile(dxString, al.toString())
           ├─ Subcódigo 4 (READ)    →  disk.readFile(dxString) → ac
           └─ Subcódigo 5 (DELETE)  →  disk.deleteFile(dxString)
           │
           ▼
  CycleEvent.fileOperation(ah, dxString)
           │
           ▼
  Kernel.handleCycleEvent(FILE_OPERATION, process, tick)
           ├─ ioManager.submitFileRequest(process, subcode, filename)
           ├─ stateManager.blockOnIO(process)  →  RUNNING → BLOCKED
           ├─ cpu.releaseProcess()
           └─ fireFileOperation(process, opName, filename)
                 Log: "[I/O] PID N → CREAR|LEER|ESCRIBIR|ABRIR|ELIMINAR 'filename'"

[5 ticks después, IOManager completa la operación]
           │
           ▼
  device.checkComplete() == true
  Kernel: stateManager.unblockToReady(process)  →  BLOCKED → READY
  Log: "[I/O] PID N desbloqueado tras E/S de disco"
```

---

## 9. Escenario 7 — Reinicio del Sistema

```
Usuario clic «Reiniciar»
           │
           ▼
  SimuladorController.reset()
           ├─ autoTimeline.stop()
           ├─ kernel.reset()
           │     ├─ clock.reset()  →  tick = 0
           │     ├─ PCB.resetPidCounter()  →  PID autoincrement reinicia en 1
           │     ├─ queueManager: limpia todas las colas
           │     ├─ cpu.releaseProcess()
           │     ├─ memoryManager.reset()  →  limpia RAM
           │     ├─ disk.reset()  →  limpia disco y directorio
           │     ├─ ioManager.reset()  →  libera dispositivos
           │     ├─ statisticsManager.reset()  →  contadores a 0
           │     ├─ dispatcher.reset()  →  contextSwitches = 0
           │     ├─ interruptManager.clear()
           │     ├─ booted = false
           │     └─ halted = false
           │
           └─ UI: limpia newQueueRows, readyQueueRows, blockedQueueRows,
                  terminatedQueueRows, memoryRows, diskRows, eventLogItems
                  currentTickProp.set(0)
```

---

## 10. Escenario 8 — Error de Ejecución en Proceso

```
CPU ejecuta instrucción con error
  (ej: PUSH con pila llena → StackOverflowError)
           │
           ▼
  CPU.applyInstruction() captura la excepción
           └─ retorna CycleEvent.error("Stack overflow en PUSH en línea N")
           │
           ▼
  Kernel.handleCycleEvent(ERROR, process, tick)
           ├─ cpu.releaseProcess()
           ├─ fireExecutionError(process, mensaje)
           │     Log: "[ERROR] PID N 'nombre': Stack overflow en PUSH en línea N"
           ├─ statisticsManager.onProcessFinished(process, tick)
           ├─ stateManager.terminate(process)  →  TERMINATED
           └─ memoryManager.free(process)  →  RAM liberada
```

**El error es completamente aislado**: los demás procesos en otras colas no se ven afectados y continúan su ejecución en los siguientes ticks.

---

## 11. Diagrama de Transición de Estados del Proceso (vista usuario)

```
                   ┌─────────────────────────────────────────────────────┐
                   │  CARGA VIA «Cargar archivos»                        │
                   └────────────────────┬────────────────────────────────┘
                                        │
                                        ▼
                                   ┌─────────┐
                                   │   NEW   │  ← PCB creado, en newQueue
                                   └────┬────┘
                                        │  RAM disponible + arrivalTime <= tick
                                        ▼
                                   ┌─────────┐
                          ┌───────►│  READY  │◄─────────────┐
                          │        └────┬────┘              │
                          │             │  schedulada       │  E/S completada
                          │             ▼                   │  o teclado recibido
                     Quantum      ┌─────────┐         ┌─────────┐
                     expirado     │ RUNNING │────────►│ BLOCKED │
                                  └────┬────┘  INT09H  └─────────┘
                                       │       INT21H
                                       │  INT 20H / PC fuera de rango / Error
                                       ▼
                                  ┌────────────┐
                                  │ TERMINATED │  ← Estadísticas calculadas
                                  └────────────┘    Memoria liberada
```
