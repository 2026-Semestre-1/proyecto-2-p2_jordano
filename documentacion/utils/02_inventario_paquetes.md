# 02 — Inventario de Paquetes

---

## 1. Mapa de Paquetes

```
simuladorminipc (raíz)
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

Total: 14 paquetes (incluyendo la raíz), 42 clases Java.

---

## 2. Paquete `simuladorminipc` (raíz)

### Responsabilidad
Punto de arranque de la JVM. Contiene únicamente la clase que posee el método `main`.

### Archivos internos

| Clase | Responsabilidad |
|-------|----------------|
| `MainFrame.java` | Contiene el único `main(String[])` de la aplicación. Su única instrucción es delegar en `SimuladorApp.main(args)`. Permite ejecutar la aplicación como JAR sin referencia directa a JavaFX en el manifiesto. |

### Colaboraciones
- Invoca → `simuladorminipc.fx.SimuladorApp`

---

## 3. Paquete `simuladorminipc.assembler`

### Responsabilidad
Análisis léxico y sintáctico del lenguaje ensamblador propio. Transforma un archivo de texto `.asm` en una lista de objetos `Instruction` listos para ser ejecutados por la CPU simulada.

### Archivos internos

| Clase | Responsabilidad |
|-------|----------------|
| `Assembler.java` | Clase utilitaria estática. Método `parse(File)` que abre, tokeniza y valida cada línea; construye la lista de instrucciones. El límite de instrucciones se lee de `assembler-config.json` vía `AssemblerConfig`. |
| `AssemblerConfig.java` | Lector de configuración del ensamblador. Carga `assembler-config.json` del classpath; expone `maxInstructions` (por defecto 80; 0 = sin límite). |
| `assembler-config.json` | Archivo de configuración del ensamblador. Define `maxInstructions`. |
| `Instruction.java` | Objeto de valor inmutable que representa una instrucción ya procesada: tipo, operandos, línea fuente, peso de ejecución. |
| `InstructionType.java` | Enumeración con los 19 tipos de instrucción y su peso de CPU asociado. |

### Colaboraciones
- `Assembler` es invocado por → `SimuladorController.loadFiles()`
- Produce `List<Instruction>` almacenado en → `PCB.instructions`
- Los pesos de `InstructionType` determinan → `PCB.burstTime`

---

## 4. Paquete `simuladorminipc.clock`

### Responsabilidad
Reloj global del simulador. Provee un contador de ticks monotónico que sirve como referencia temporal para todos los subsistemas.

### Archivos internos

| Clase | Responsabilidad |
|-------|----------------|
| `SystemClock.java` | Mantiene un `long` interno. Métodos: `tick()` incrementa y devuelve el valor; `reset()` vuelve a cero; `getCurrentTick()` consulta sin incrementar. |

### Colaboraciones
- Instanciado dentro de → `Kernel`
- Tick actual pasado como parámetro a → `ProcessManager`, `SchedulingPolicyManager`, `StatisticsManager`

---

## 5. Paquete `simuladorminipc.cpu`

### Responsabilidad
Modela la Unidad Central de Procesamiento. Ejecuta instrucciones una por una de acuerdo con los pesos de cada operación, genera eventos de ciclo (resultado de la ejecución) y gestiona el despacho/retiro de procesos.

### Archivos internos

| Clase | Responsabilidad |
|-------|----------------|
| `CPU.java` | Unidad de CPU singular. Mantiene referencia al proceso en ejecución. `executeCycle()` descuenta peso y llama a `applyInstruction()` cuando el contador llega a cero. |
| `CycleEvent.java` | Objeto de valor inmutable que encapsula el resultado de un ciclo: tipo de resultado + carga útil opcional (texto, error, subcódigo de archivo). Fábricas estáticas para cada tipo. |
| `CycleResult.java` | Enumeración de los 7 resultados posibles: `NORMAL`, `PROCESS_FINISHED`, `SCREEN_OUTPUT`, `KEYBOARD_INPUT`, `FILE_OPERATION`, `IDLE`, `ERROR`. |
| `Dispatcher.java` | Gestiona los cambios de contexto formales: `dispatch(PCB, CPU)` carga el proceso en la CPU; `preempt(CPU)` lo retira; contabiliza cambios de contexto. |

### Colaboraciones
- `CPU` lee instrucciones de → `PCB.instructions` vía `PCB.registers.pc`
- `CPU` modifica → `PCB.registers` (AC, AX, BX, etc.)
- `CPU` interactúa con → `Disk` (operaciones `INT 21H`)
- `Dispatcher` es invocado por → `Kernel.scheduleAndDispatch()`
- `CycleEvent` es consumido por → `Kernel.handleCycleEvent()`

---

## 6. Paquete `simuladorminipc.fx`

### Responsabilidad
Capa de presentación. Construye la interfaz gráfica con JavaFX, vincula los observables del controlador a los componentes visuales, y gestiona todos los eventos de interacción del usuario.

### Archivos internos

| Clase | Responsabilidad |
|-------|----------------|
| `SimuladorApp.java` | Extiende `Application`. Crea la ventana de 1440×860 px, la barra de herramientas, el panel izquierdo con BCP y registro de eventos, la sección central con colas verticales con scroll, y el panel derecho de RAM/Disco. Usa `SplitPane` para permitir redimensionamiento en tiempo de ejecución. |
| `SimuladorController.java` | Instancia y posee el `Kernel`. Implementa `KernelEventListener`. Expone propiedades observables de JavaFX (`StringProperty`, `ObservableList`) a las que la vista se vincula. Contiene las clases internas `ProcessRow`, `MemoryRow`, `DiskRow`. |

### Colaboraciones
- `SimuladorController` posee → `Kernel`
- `SimuladorController` registra `this` como → `KernelEventListener`
- `SimuladorController` invoca → `Assembler.parse()` en `loadFiles()`
- La vista vincula sus componentes a → propiedades de `SimuladorController`

---

## 7. Paquete `simuladorminipc.interrupt`

### Responsabilidad
Sistema de interrupciones interno del kernel. Encola eventos asincrónicos (quantum expirado, E/S completada, error de ejecución) para ser procesados durante el ciclo de tick.

### Archivos internos

| Clase | Responsabilidad |
|-------|----------------|
| `Interrupt.java` | Objeto de valor inmutable: tipo de interrupción, PCB fuente, carga útil (String), valor entero auxiliar. |
| `InterruptManager.java` | Cola FIFO implementada con `ArrayDeque`. Métodos: `raise()`, `poll()`, `peek()`, `clear()`, `snapshot()`. |
| `InterruptType.java` | Enumeración de los 9 tipos: `PROCESS_FINISHED`, `QUANTUM_EXPIRED`, `IO_REQUEST`, `IO_FINISHED`, `PAGE_FAULT`, `KEYBOARD_INPUT`, `FILE_OPERATION`, `SCREEN_OUTPUT`, `RUNTIME_ERROR`. |

### Colaboraciones
- `InterruptManager` es instanciado dentro de → `Kernel`
- `Kernel.handleCycleEvent()` invoca → `interruptManager.raise()`
- `Kernel.executeTick()` consume → `interruptManager.poll()`

---

## 8. Paquete `simuladorminipc.io`

### Responsabilidad
Modela los dispositivos de entrada/salida con latencias variables. Gestiona la cola de operaciones de E/S y el ciclo de vida de los procesos bloqueados esperando un dispositivo.

### Archivos internos

| Clase | Responsabilidad |
|-------|----------------|
| `Device.java` | Representa un único dispositivo (TECLADO=3, PANTALLA=1, DISCO=5 ticks de latencia). Acepta un `IORequest`, descuenta ticks y señala cuando la operación completa. |
| `IOManager.java` | Posee tres instancias de `Device` más un `Map<Integer, PCB>` de procesos en espera. Gestiona solicitudes de teclado (duración MAX_VALUE, no completan automáticamente), solicitudes de archivos y el tick de todos los dispositivos. |

### Colaboraciones
- `IOManager` es instanciado dentro de → `Kernel`
- `CPU.applyInstruction()` desencadena solicitudes vía → `Kernel.handleCycleEvent(IO_REQUEST)`
- `IOManager.tick()` retorna procesos desbloqueados a → `Kernel`
- `Kernel.provideKeyboardInput()` invoca → `IOManager.completeKeyboardRequest()`

---

## 9. Paquete `simuladorminipc.kernel`

### Responsabilidad
Núcleo orquestador del simulador. Coordina todos los subsistemas en un ciclo de 10 etapas denominado `executeTick()`. Es la fachada principal que oculta la complejidad interna.

### Archivos internos

| Clase | Responsabilidad |
|-------|----------------|
| `Kernel.java` | Propietario de todas las instancias de los subsistemas. Implementa el pipeline de simulación ciclo a ciclo. Gestiona el estado `booted`, `halted`, `waitingForKeyboard`. Notifica a los listeners registrados. |
| `KernelEventListener.java` | Interfaz observadora con 7 métodos de callback: `onTickCompleted`, `onProcessStateChanged`, `onScreenOutput`, `onKeyboardInputRequired`, `onFileOperation`, `onExecutionError`, `onAllFinished`. |

### Colaboraciones
- `Kernel` posee → `SystemClock`, `ProcessManager`, `QueueManager`, `StateManager`, `SchedulingPolicyManager`, `Dispatcher`, `CPU`, `IOManager`, `InterruptManager`, `MemoryManager`, `VirtualMemoryManager`, `SwapManager`, `Disk`, `StatisticsManager`
- `Kernel` notifica a → `List<KernelEventListener>` (implementado por `SimuladorController`)

---

## 10. Paquete `simuladorminipc.memory`

### Responsabilidad
Jerarquía de memoria del sistema simulado: RAM física, asignador por primer ajuste, configuración por JSON, traducción de direcciones virtuales y gestor de intercambio (swap).

### Archivos internos

| Clase | Responsabilidad |
|-------|----------------|
| `RAM.java` | Arreglo de `String` de tamaño configurable. Reserva las primeras 20 celdas para el SO. Métodos `read()`, `write()`, `clearRange()`. |
| `MemoryManager.java` | Asignador por primer ajuste. `allocate(PCB)` busca un bloque libre contiguo; `free(PCB)` marca el bloque como disponible; `updateBcpOsArea(PCB)` escribe el BCP del proceso en las celdas 0–19. |
| `MemoryBlock.java` | Descriptor de bloque: dirección base, tamaño, PID propietario, bandera `free`. |
| `MemoryConfig.java` | Lee `memory-config.json` del classpath usando expresiones regulares. Si el archivo no existe usa valores por defecto (RAM=512, VMem=64, Disco=512). |
| `VirtualMemoryManager.java` | Traduce direcciones virtuales a físicas: `translate(PCB, offset) = PCB.memoryBase + offset`. Valida que el offset esté dentro del bloque asignado. |
| `SwapManager.java` | Marcador de posición (*placeholder*). Métodos `swapOut()` y `swapIn()` definidos pero no completamente implementados en la versión actual. |

### Colaboraciones
- `MemoryConfig` es leído en la construcción de → `Kernel`
- `MemoryManager` es invocado por → `Kernel.admitToMemory()` y `Kernel.handleCycleEvent(PROCESS_FINISHED/ERROR)`
- `VirtualMemoryManager` es consultado por → `CPU.applyInstruction()` para validar accesos a memoria

---

## 11. Paquete `simuladorminipc.model`

### Responsabilidad
Entidades de datos del sistema operativo simulado. Define los objetos que representan procesos, estados, registros y solicitudes de E/S.

### Archivos internos

| Clase | Responsabilidad |
|-------|----------------|
| `PCB.java` | Bloque de Control de Proceso. Contiene todos los metadatos de un proceso: PID, nombre, estado, tiempos, registros, instrucciones, información de memoria, archivos abiertos, solicitudes de E/S. |
| `ProcessState.java` | Enumeración de los 7 estados del proceso: `NEW`, `READY`, `RUNNING`, `BLOCKED`, `SUSPENDED_READY`, `SUSPENDED_BLOCKED`, `TERMINATED`. Cada estado tiene un `displayName` en español. |
| `RegisterSet.java` | Conjunto de registros de la CPU para un proceso: `ac`, `ax`, `bx`, `cx`, `dx`, `ir`, `pc`, `ah`, `al`, `dxString`, `zeroFlag`, `stack[5]`, `stackPointer`. |
| `IORequest.java` | Solicitud de E/S: tipo de operación (`READ`, `WRITE`, `CREATE`, `DELETE`, `KEYBOARD`, `SCREEN`), cuenta regresiva de ticks, bandera de completado. |

### Colaboraciones
- `PCB` es creado por → `SimuladorController.loadFiles()`
- `PCB` es manipulado por prácticamente todos los paquetes
- `RegisterSet` es modificado por → `CPU.applyInstruction()`
- `IORequest` es creado por → `CPU` y gestionado por → `IOManager`

---

## 12. Paquete `simuladorminipc.process`

### Responsabilidad
Gestión del ciclo de vida de los procesos y consistencia de las colas. Orquesta cuándo un proceso es admitido, cuándo puede ejecutarse y cuándo ha finalizado.

### Archivos internos

| Clase | Responsabilidad |
|-------|----------------|
| `ProcessManager.java` | Admite procesos en cola NEW, controla el máximo de 5 procesos concurrentes, ejecuta el pase de admisión en cada tick (procesos con `arrivalTime <= tick` y RAM disponible pasan a READY). |
| `QueueManager.java` | Posee y gestiona las 5 colas de procesos (NEW como `LinkedList`, READY/BLOCKED/SUSPENDED/TERMINATED como `ArrayList`). Métodos de movimiento entre colas. |
| `StateManager.java` | Impone las transiciones legales de estado: `admitToReady()`, `setRunning()`, `preemptToReady()`, `blockOnIO()`, `unblockToReady()`, `terminate()`. Lanza `IllegalStateException` en transiciones inválidas. |

### Colaboraciones
- `ProcessManager` es invocado por → `Kernel.executeTick()` (etapa 2)
- `QueueManager` es consultado por → `SchedulingPolicyManager.selectNext()`
- `StateManager` es invocado por → `Kernel` en múltiples etapas del pipeline

---

## 13. Paquete `simuladorminipc.scheduler`

### Responsabilidad
Algoritmos de planificación de CPU. Define la interfaz común e implementa varias estrategias. La versión documentada de la interfaz gráfica expone FCFS como política visible, aunque la infraestructura de selección permanece en el código.

### Archivos internos

| Clase | Responsabilidad |
|-------|----------------|
| `SchedulingAlgorithm.java` | Interfaz: `selectNextProcess(List<PCB>, PCB currentRunning) → PCB`, `getName()`. |
| `SchedulingPolicyManager.java` | Mantiene la instancia activa del algoritmo. Enum interno `Policy` con los 6 valores. `setPolicy()` e instanciación dinámica. |
| `FCFSScheduler.java` | FCFS no apropiativo: selecciona el proceso con menor `arrivalTime` (desempate: menor PID). |
| `RoundRobinScheduler.java` | RR apropiativo: selecciona el proceso al frente de la cola cuando el quantum expira. Constructor toma `int quantum`. |
| `SPNScheduler.java` | SPN no apropiativo: selecciona el proceso con menor `burstTime`. |
| `SRTScheduler.java` | SRT apropiativo: selecciona el proceso con menor `remainingTime`; puede desalojar al proceso en ejecución. |
| `HRRNScheduler.java` | HRRN no apropiativo: selecciona el proceso con mayor ratio `(waitingTime + burstTime) / burstTime`. |
| `PriorityScheduler.java` | Prioridad no apropiativa: selecciona el proceso con menor número de prioridad. |

### Colaboraciones
- `SchedulingPolicyManager.selectNext()` es invocado por → `Kernel.scheduleAndDispatch()`
- Los schedulers consultan campos de → `PCB` (arrivalTime, burstTime, remainingTime, priority, waitingTime)

---

## 14. Paquete `simuladorminipc.stats`

### Responsabilidad
Cálculo y almacenamiento de métricas de rendimiento del sistema y de cada proceso individual.

### Archivos internos

| Clase | Responsabilidad |
|-------|----------------|
| `StatisticsManager.java` | Acumula ticks totales vs. ticks activos. Calcula utilización de CPU, productividad, tiempo promedio de espera, tiempo de retorno y tiempo de respuesta al momento de consulta. |
| `ProcessStats.java` | Objeto de valor inmutable que representa una instantánea de las estadísticas de un proceso al finalizar: PID, nombre, prioridad, tiempos de inicio/fin, ráfaga, espera, retorno, respuesta, servicio, ratio de retorno. |

### Colaboraciones
- `StatisticsManager` es invocado por → `Kernel.executeTick()` (etapa 8)
- `StatisticsManager.onProcessFinished()` captura → `ProcessStats` de cada PCB terminado
- `SimuladorController` consulta `StatisticsManager` vía callbacks del listener para actualizar la UI

---

## 15. Paquete `simuladorminipc.storage`

### Responsabilidad
Disco secundario simulado. Implementa un sistema de archivos simplificado con directorio y área de datos.

### Archivos internos

| Clase | Responsabilidad |
|-------|----------------|
| `Disk.java` | Arreglo de `String` de tamaño configurable. Las primeras 10 celdas forman el índice de directorio; el resto almacena contenido. Métodos: `createFile()`, `openFile()`, `writeFile()`, `readFile()`, `deleteFile()`, `storeProgram()`. |
| `DiskFile.java` | Descriptor de archivo: nombre, dirección de inicio, tamaño, contenido. `setContent()` actualiza el tamaño automáticamente. |

### Colaboraciones
- `Disk` es instanciado dentro de → `Kernel`
- `CPU.applyInstruction()` invoca operaciones de disco vía → `Kernel.handleCycleEvent(FILE_OPERATION)`
- El mapa visual del disco en la UI se obtiene de → `Disk.getStorage()`

---

## 16. Diagrama de Colaboración entre Paquetes

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                              simuladorminipc.fx                                  │
│              SimuladorApp ◄──────────── SimuladorController                     │
└─────────────────────────────────────────────┬────────────────────────────────────┘
                                              │ crea / controla
                                              ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                              simuladorminipc.kernel                              │
│                                    Kernel.java                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐         │
│  │ assembler│  │  clock   │  │   cpu    │  │ interrupt│  │    io    │         │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘         │
│       │              │              │              │              │               │
│  ┌────▼─────┐  ┌─────▼────┐  ┌─────▼────┐  ┌─────▼────┐  ┌────▼─────┐        │
│  │  memory  │  │  model   │  │ process  │  │scheduler │  │  stats   │         │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘         │
│                                                          ┌──────────┐           │
│                                                          │ storage  │           │
│                                                          └──────────┘           │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Flujo de datos principal

```
Usuario
  │ selecciona archivo .asm
  ▼
assembler.Assembler.parse()
  │ produce List<Instruction>
  ▼
model.PCB (creado con instrucciones + metadatos)
  │ enviado a
  ▼
process.ProcessManager.submit()
  │ admitido en
  ▼
process.QueueManager (cola NEW → READY)
  │ seleccionado por
  ▼
scheduler.SchedulingPolicyManager.selectNext()
  │ despachado por
  ▼
cpu.Dispatcher.dispatch() → cpu.CPU.executeCycle()
  │ produce
  ▼
cpu.CycleEvent
  │ procesado por
  ▼
kernel.Kernel.handleCycleEvent()
  │ puede generar
  ├── interrupt → interrupt.InterruptManager
  ├── io → io.IOManager
  ├── stats → stats.StatisticsManager
  └── notify → fx.SimuladorController (KernelEventListener)
```
