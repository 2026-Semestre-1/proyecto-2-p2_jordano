# Manual Técnico
# Simulador de Gestión de Procesos — Mini PC

---

## 1. Visión General de la Arquitectura

El sistema está implementado siguiendo el patrón **MVC** extendido con el patrón **Observer** para desacoplar completamente el núcleo de simulación de la interfaz gráfica:

```
┌──────────────────────────────────────────────────────────────────────────┐
│  VISTA (JavaFX Scene Graph)                                              │
│  SimuladorApp — construye nodos, vincula ObservableList/Property         │
│        │                                                                 │
│        │ posee y vincula                                                 │
│        ▼                                                                 │
│  CONTROLADOR (MVP / Presenter)                                           │
│  SimuladorController — implementa KernelEventListener                   │
│        │                                                                 │
│        │ posee y delega                                                  │
│        ▼                                                                 │
│  MODELO (Fachada + subsistemas)                                          │
│  Kernel — orquesta el pipeline de 10 etapas                              │
│  ├── assembler  ├── clock    ├── cpu       ├── interrupt                 │
│  ├── io         ├── memory   ├── model     ├── process                  │
│  ├── scheduler  ├── stats    └── storage                                │
└──────────────────────────────────────────────────────────────────────────┘
```

### Patrones de diseño aplicados

| Patrón | Implementación |
|--------|----------------|
| **MVC** | `SimuladorApp` (View), `SimuladorController` (Controller), `Kernel` + subsistemas (Model) |
| **Observer** | `KernelEventListener` — 7 callbacks; implementado por `SimuladorController` |
| **Facade** | `Kernel` expone una API simple para una red de 14 subsistemas |
| **Strategy** | `SchedulingAlgorithm` — interfaz implementada por 6 planificadores intercambiables |
| **Value Object** | `Instruction`, `CycleEvent`, `ProcessStats`, `Interrupt`, `DiskFile` — inmutables con fábricas estáticas |
| **Factory Method** | Fábricas en `CycleEvent`: `.normal()`, `.error(msg)`, `.screenOutput(text)`, etc. |
| **Command** | Botones de toolbar invocan métodos del controlador (`step()`, `toggleAuto()`, `reset()`) |
| **Template Method** | `Kernel.executeTick()` define las 10 etapas; métodos privados implementan cada paso |

---

## 2. Pila Tecnológica

| Componente | Versión | Propósito |
|------------|---------|-----------|
| Java | 25 | Lenguaje principal |
| JavaFX | 24 | UI: Scene Graph, bindings reactivos, Timeline, TableView |
| NetBeans Ant | — | Compilación y empaquetado (build.xml) |
| JSON (manual) | — | `MemoryConfig` y `AssemblerConfig` usan parseo con regex; sin dependencias externas |
| PlantUML | — | Diagramas UML en `documentacion/diagramas/` |

---

## 3. Diagrama de Paquetes

> Ver archivo: `documentacion/diagramas/diagrama_paquetes.puml`

```
simuladorminipc (raíz)               «punto de entrada JVM»
├── assembler                        «analizador léxico/sintáctico»
├── clock                            «reloj monotónico global»
├── cpu                              «unidad de procesamiento»
├── fx                               «capa de presentación JavaFX»
├── interrupt                        «cola FIFO de interrupciones»
├── io                               «dispositivos de E/S con latencias»
├── kernel                           «núcleo orquestador — Fachada»
├── memory                           «jerarquía de memoria»
├── model                            «entidades del SO (PCB, registros, etc.)»
├── process                          «ciclo de vida de procesos»
├── scheduler                        «algoritmos de planificación — Strategy»
├── stats                            «métricas de rendimiento»
└── storage                          «disco secundario simulado»
```

**Flujo de dependencias entre capas (simplificado)**:

```
fx ──────────────────────► kernel
                              │
           ┌──────────────────┼──────────────────────┐
           ▼                  ▼                       ▼
       assembler            cpu                    memory
           │                  │                       │
           ▼                  ▼                       ▼
         model            interrupt               process
                              │                       │
                              ▼                       ▼
                             io                  scheduler
                                                      │
                                                      ▼
                                                   stats
                                                   storage
```

---

## 4. Diagrama de Clases UML (resumen estructural)

> Ver archivo completo: `documentacion/diagramas/diagrama_clases_completo.puml`

A continuación se presenta un extracto del diagrama de clases con las relaciones principales:

```
┌──────────────────────────────────────────────────────────────────────┐
│ SimuladorApp                                                         │
│ ─────────────────────────────────────────────────────────────────── │
│ - ctrl : SimuladorController                                         │
│ + start(Stage) : void                                                │
│ + main(String[]) : void                                              │
└──────────────────────────┬───────────────────────────────────────────┘
                           │ posee
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│ SimuladorController  <<KernelEventListener>>                         │
│ ─────────────────────────────────────────────────────────────────── │
│ - kernel : Kernel                                                    │
│ - autoTimeline : Timeline                                            │
│ - newQueueRows, readyQueueRows, blockedQueueRows,                    │
│   terminatedQueueRows : ObservableList<ProcessRow>                   │
│ - memoryRows : ObservableList<MemoryRow>                             │
│ - diskRows : ObservableList<DiskRow>                                 │
│ - currentTickProp : LongProperty                                     │
│ - cpuStatusProp, cpuProcessProp, reg*Prop : StringProperty           │
│ + loadFiles() : void                                                 │
│ + step() : void                                                      │
│ + toggleAuto() : void                                                │
│ + startSingleProcess() : void                                        │
│ + reset() : void                                                     │
│ + provideKeyboardInput(int) : void                                   │
│ + onTickCompleted(long) : void         [KernelEventListener]         │
│ + onProcessStateChanged(PCB,...) : void                              │
│ + onScreenOutput(PCB, String) : void                                 │
│ + onKeyboardInputRequired(PCB) : void                                │
│ + onAllFinished() : void                                             │
└──────────────────────────┬───────────────────────────────────────────┘
                           │ posee
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│ Kernel                                                               │
│ ─────────────────────────────────────────────────────────────────── │
│ - clock : SystemClock                                                │
│ - processManager : ProcessManager                                    │
│ - queueManager : QueueManager                                        │
│ - stateManager : StateManager                                        │
│ - scheduler : SchedulingPolicyManager                                │
│ - dispatcher : Dispatcher                                            │
│ - cpu : CPU                                                          │
│ - ioManager : IOManager                                              │
│ - interruptManager : InterruptManager                                │
│ - memoryManager : MemoryManager                                      │
│ - vmm : VirtualMemoryManager                                         │
│ - swapManager : SwapManager                                          │
│ - disk : Disk                                                        │
│ - statisticsManager : StatisticsManager                              │
│ - listeners : List<KernelEventListener>                              │
│ - booted, halted : boolean                                           │
│ - waitingForKeyboard : PCB                                           │
│ + boot() : void                                                      │
│ + executeTick() : void                                               │
│ + loadProcess(PCB) : void                                            │
│ + provideKeyboardInput(int) : void                                   │
│ + reset() : void                                                     │
│ + setPolicy(Policy) : void                                           │
│ + setQuantum(int) : void                                             │
└──────────────────────────────────────────────────────────────────────┘
```

**Relaciones clave de composición**:

```
Kernel ──────────────────── 1 SystemClock
       ──────────────────── 1 ProcessManager
       ──────────────────── 1 QueueManager
       ──────────────────── 1 StateManager
       ──────────────────── 1 SchedulingPolicyManager ──► SchedulingAlgorithm «interface»
       ──────────────────── 1 Dispatcher
       ──────────────────── 1 CPU ──────────────────────► Disk, VirtualMemoryManager
       ──────────────────── 1 IOManager ─────────────────► 3 Device
       ──────────────────── 1 InterruptManager
       ──────────────────── 1 MemoryManager ──────────────► RAM, List<MemoryBlock>
       ──────────────────── 1 VirtualMemoryManager
       ──────────────────── 1 SwapManager
       ──────────────────── 1 Disk ────────────────────────► Map<String,DiskFile>
       ──────────────────── 1 StatisticsManager ────────────► Dispatcher (lectura)

PCB ─────────────────────── 1 RegisterSet
    ─────────────────────── n Instruction
    ─────────────────────── 0..1 IORequest (currentIORequest)
```

**Jerarquía de planificadores**:

```
«interface»
SchedulingAlgorithm
    + selectNextProcess(List<PCB>, PCB) : PCB
    + getName() : String
         ▲
         │ implements
    ┌────┴────────────────────────────────────┐
    │    │         │         │       │        │
FCFSScheduler  RoundRobin  SPN    SRT     HRRN    Priority
               Scheduler Scheduler Scheduler Scheduler Scheduler
```

---

## 5. Diagrama de Secuencia — Carga de Archivo

> Ver archivo completo: `documentacion/diagramas/diagrama_secuencia_ejecucion.puml`

```
Usuario    SimuladorController    Assembler     Kernel    MemoryManager    QueueManager
  │              │                   │             │            │               │
  ├──loadFiles()►│                   │             │            │               │
  │              ├──parse(file)─────►│             │            │               │
  │              │◄──List<Instruction>             │            │               │
  │              ├──new PCB(instructions)          │            │               │
  │              ├──loadProcess(PCB)──────────────►│            │               │
  │              │                                 ├──submit(PCB)              │
  │              │                                 │──────────────────────────►│
  │              │                                 │            │         admitToNew(PCB)
  │              │                                 │            │               │
  │              │                       [en el siguiente tick]  │               │
  │              │                                 │            │               │
  │              ├──step()────────────────────────►│            │               │
  │              │                                 ├──allocate(PCB)────────────►│(MemMgr)
  │              │                                 │◄──true (RAM disponible)    │
  │              │                                 ├──admitToReady(PCB)         │
  │              │                                 │────────────────────────────►(QueueMgr)
  │              │◄──onTickCompleted(tick)──────────│            │               │
  │◄──refreshUI()│                                 │            │               │
```

---

## 6. Diagrama de Secuencia — Ciclo de Ejecución (tick)

```
Kernel       CPU          SchedulingPolicyMgr    Dispatcher    StateManager    StatisticsMgr
  │            │                  │                   │              │               │
  ├─clock.tick()                  │                   │              │               │
  ├─processManager.loadArriving() │                   │              │               │
  ├─ioManager.tick()              │                   │              │               │
  │            │                  │                   │              │               │
  ├──────────────────selectNext(readyQueue)           │              │               │
  │            │                 ►│                   │              │               │
  │            │◄──next PCB────── │                   │              │               │
  │            │                  │                   │              │               │
  ├───────────────────────────────────dispatch(PCB,CPU)              │               │
  │            │                  │                  ►│              │               │
  │            │◄─loadProcess(PCB)│                   │              │               │
  │            │                  │          setRunning(PCB)         │               │
  │            │                  │                   │─────────────►│               │
  │            │                  │                   │              │               │
  ├──executeCycle()               │                   │              │               │
  │           ►│                  │                   │              │               │
  │            ├──applyInstruction(instr)             │              │               │
  │            │◄──CycleEvent     │                   │              │               │
  │◄──CycleEvent                  │                   │              │               │
  │            │                  │                   │              │               │
  ├──handleCycleEvent(ev)         │                   │              │               │
  │  (NORMAL/FINISHED/ERROR/IO/KB/SCREEN)             │              │               │
  │            │                  │                   │              │               │
  ├──onTick(tick)──────────────────────────────────────────────────────────────────►│
  ├──fireTickCompleted(tick) → SimuladorController.onTickCompleted() │               │
```

---

## 7. Algoritmos de Planificación

El sistema implementa 6 algoritmos mediante el patrón **Strategy** (`SchedulingAlgorithm`). Todos reciben la `readyQueue` y el `currentRunning` process, y devuelven el PCB seleccionado o `null` si la cola está vacía.

### 7.1 FCFS — First Come, First Served

**Tipo**: No apropiativo  
**Criterio de selección**: Proceso con menor `arrivalTime`; desempate por menor `pid`.

```
Dado: readyQueue = [P3(arrive=5), P1(arrive=2), P4(arrive=5)]

Selección:
  P1 (arrive=2) < P3 (arrive=5)
  Resultado: P1 es seleccionado
```

**Fórmula del tiempo de espera**:
$$W_i = t_{inicio} - t_{llegada}$$

**Ventajas**: Simple, sin inanición.  
**Desventajas**: Efecto convoy — procesos cortos esperan detrás de largos.

---

### 7.2 Round Robin (RR)

**Tipo**: Apropiativo  
**Criterio de selección**: Frente de la cola READY cuando el quantum expira.  
**Parámetro**: `quantum` (default: 2 ticks, configurable por `Kernel.setQuantum()`).

```
Cola READY: [P1, P2, P3]  quantum=2

Tick 1: P1 ejecuta (quantumRemaining = 2 → 1)
Tick 2: P1 ejecuta (quantumRemaining = 1 → 0) → QUANTUM_EXPIRED
        P1 vuelve al final: [P2, P3, P1]
Tick 3: P2 ejecuta
...
```

**Fórmula del tiempo de retorno**:
$$T_i = t_{fin} - t_{llegada}$$

**Ventajas**: Equitativo; buen tiempo de respuesta para todos.  
**Desventajas**: Overhead de cambios de contexto; sensible al valor del quantum.

---

### 7.3 SPN — Shortest Process Next

**Tipo**: No apropiativo  
**Criterio de selección**: Proceso con menor `burstTime` total.

```
readyQueue = [P1(burst=10), P2(burst=4), P3(burst=7)]
Selección: P2 (burst=4) — el más corto
```

**Nota**: Usa `burstTime` (ráfaga total conocida desde el parse). Minimiza el tiempo de espera promedio.  
**Desventaja potencial**: Puede generar inanición en procesos largos.

---

### 7.4 SRT — Shortest Remaining Time

**Tipo**: Apropiativo  
**Criterio de selección**: Proceso con menor `remainingTime`; puede desalojar al proceso en ejecución si llega uno con menor tiempo restante.

```
Ejecutando P1 (remaining=6)
Llega P2 (remaining=3) a READY

SRT compara: P2.remaining(3) < P1.remaining(6)
→ dispatcher.preempt(cpu)
→ P1 regresa a READY
→ dispatcher.dispatch(P2, cpu)
```

**Ventaja**: Minimiza el tiempo de retorno promedio.  
**Desventaja**: Alta frecuencia de cambios de contexto; inanición posible.

---

### 7.5 HRRN — Highest Response Ratio Next

**Tipo**: No apropiativo  
**Criterio de selección**: Proceso con mayor ratio de respuesta.

$$\text{ratio}_i = \frac{t_{espera_i} + t_{rafaga_i}}{t_{rafaga_i}}$$

```
P1: espera=8, burst=4  → ratio = (8+4)/4 = 3.0
P2: espera=2, burst=2  → ratio = (2+2)/2 = 2.0
P3: espera=0, burst=10 → ratio = (0+10)/10 = 1.0

Selección: P1 (ratio=3.0)
```

**Ventaja**: No genera inanición — los procesos con mayor espera acumulada aumentan su ratio.

---

### 7.6 Priority Scheduling

**Tipo**: No apropiativo (en la implementación actual)  
**Criterio de selección**: Proceso con menor valor numérico de `priority` (menor número = mayor prioridad).

```
readyQueue = [P1(priority=3), P2(priority=1), P3(priority=2)]
Selección: P2 (priority=1)
```

**Nota**: La prioridad se define en el PCB con valor por defecto 0. En la versión actual, todos los procesos creados desde archivos `.asm` tienen `priority=0`, por lo que el desempate es por `pid`.

---

## 8. Gestión de Memoria

### 8.1 Estructura de la RAM

```
Dirección  │ Zona        │ Contenido
──────────────────────────────────────────────────
0   – 19   │ Área del SO │ BCPs de procesos activos
20  – 699  │ Usuario     │ Instrucciones de procesos
```

### 8.2 Algoritmo First-Fit

```
Al admitir proceso P (memoryRequired = N):
  Para cada bloque b en blocks (en orden de dirección ascendente):
    Si b.free && b.size >= N:
      b.free = false
      b.pid = P.pid
      P.memoryBase = b.base
      P.memoryLimit = b.base + b.size - 1
      Escribe instrucciones en RAM[b.base .. b.base+N-1]
      return true
  return false  ← proceso permanece en NEW
```

### 8.3 Traducción de Dirección Virtual

```
Dirección física = P.memoryBase + virtualOffset

Validación previa:
  if virtualOffset < 0 || virtualOffset >= (P.memoryLimit - P.memoryBase):
    → error de acceso a memoria (CycleEvent.error)
```

### 8.4 Liberación de Memoria

Al terminar o cancelar un proceso:
```
memoryManager.free(P):
  Busca bloque con b.pid == P.pid
  b.free = true
  b.pid = -1
  RAM.clearRange(b.base, b.base + b.size)
```

---

## 9. Sistema de Interrupciones

### 9.1 Tipos de interrupción (`InterruptType`)

| Tipo | Generado cuando |
|------|----------------|
| `PROCESS_FINISHED` | El proceso ejecuta `INT 20H` o el PC supera el límite |
| `QUANTUM_EXPIRED` | El contador de quantum llega a 0 (Round Robin) |
| `IO_REQUEST` | El proceso ejecuta `INT 09H` o `INT 21H` |
| `IO_FINISHED` | Un dispositivo completa su countdown |
| `PAGE_FAULT` | Acceso a página no cargada (base para futura extensión) |
| `KEYBOARD_INPUT` | Respuesta del usuario al campo de teclado |
| `FILE_OPERATION` | Subcódigo de operación de disco |
| `SCREEN_OUTPUT` | El proceso ejecuta `INT 10H` |
| `RUNTIME_ERROR` | Stack overflow, underflow, instrucción inválida |

### 9.2 Ciclo de vida de una interrupción

```
CPU.applyInstruction()
    └─ Produce CycleEvent (ej: keyboardInput())
           │
           ▼
Kernel.handleCycleEvent()
    └─ Llama: interruptManager.raise(KEYBOARD_INPUT, process, null, 0)
           │
           ▼
InterruptManager (cola ArrayDeque)
    └─ Almacena Interrupt{type, sourcePCB, payload, intValue}
           │
           ▼ [consumido en el mismo tick o siguiente]
Kernel.executeTick()
    └─ poll() → procesa la interrupción correspondiente
```

---

## 10. Subsistema de E/S

### Latencias de dispositivos

| Dispositivo | Latencia (ticks) | Característica especial |
|-------------|-----------------|------------------------|
| Teclado | `Integer.MAX_VALUE` | No completa automáticamente; espera `provideKeyboardInput()` |
| Pantalla | 1 | No bloquea al proceso (completa en el mismo tick) |
| Disco | 5 | Bloquea el proceso durante 5 ticks |

### Flujo de una solicitud de disco

```
Tick N:   CPU → INT 21H → CycleEvent.fileOperation(subcode, filename)
          Kernel: ioManager.submitFileRequest(P, subcode, filename)
                  device.assign(IORequest, P.pid)
                  stateManager.blockOnIO(P) → BLOCKED

Tick N+1: ioManager.tick() → device.countdown--  (4 restantes)
Tick N+2: ioManager.tick() → device.countdown--  (3 restantes)
Tick N+3: ioManager.tick() → device.countdown--  (2 restantes)
Tick N+4: ioManager.tick() → device.countdown--  (1 restante)
Tick N+5: ioManager.tick() → device.countdown-- → checkComplete() = true
          Kernel: stateManager.unblockToReady(P) → READY
                  device.release()
```

---

## 11. Diagrama de Transición de Estados

```
                        ┌──────────────────────────────────────────────┐
                        │   Carga vía loadFiles()                      │
                        └───────────────────┬──────────────────────────┘
                                            │
                                            ▼
                                     ┌─────────────┐
                                     │     NEW      │  ← PCB en newQueue
                                     └──────┬───────┘
                                            │  arrivalTime <= tick
                                            │  && RAM disponible
                                            │  && concurrentes < 5
                                            ▼
                        ┌───────────►┌─────────────┐
                        │            │    READY     │  ← en readyQueue
                        │            └──────┬───────┘
                        │                   │  selectNext() + dispatch()
                   Quantum                  ▼
                  expirado         ┌─────────────────┐
                   /preempt        │    RUNNING       │  ← en CPU
                        │         └───┬──────────┬───┘
                        │             │          │
                        │    INT 20H  │          │  INT 09H
                        │    PC>=max  │          │  INT 21H
                        │    Error    │          ▼
                        │             │    ┌─────────────┐
                        │             │    │   BLOCKED    │  ← IOManager
                        │             │    └──────┬───────┘
                        │             │           │  E/S completada
                        └─────────────┼───────────┘  o teclado recibido
                                      │
                                      ▼
                               ┌────────────────┐
                               │   TERMINATED   │  ← memoria liberada
                               └────────────────┘    estadísticas calculadas
```

---

## 12. Métricas de Rendimiento

El `StatisticsManager` calcula las siguientes métricas al finalizar la simulación:

| Métrica | Fórmula |
|---------|---------|
| Utilización de CPU | $\text{CPU\%} = \frac{activeTicks}{totalTicks} \times 100$ |
| Productividad (throughput) | $\text{TP} = \frac{n_{terminados}}{totalTicks}$ |
| Tiempo de espera promedio | $\overline{W} = \frac{1}{n}\sum_{i=1}^{n} waitingTime_i$ |
| Tiempo de retorno promedio | $\overline{T} = \frac{1}{n}\sum_{i=1}^{n} turnaroundTime_i$ |
| Tiempo de respuesta promedio | $\overline{R} = \frac{1}{n}\sum_{i=1}^{n} responseTime_i$ |
| Ratio de retorno | $\rho_i = \frac{turnaroundTime_i}{burstTime_i}$ |

**Definiciones**:
- $turnaroundTime_i = endTime_i - arrivalTime_i$
- $responseTime_i = startTime_i - arrivalTime_i$ (primera vez en CPU)
- $waitingTime_i$ = ticks acumulados en cola READY sin ejecutar
- $serviceTime_i$ = ticks reales de CPU ejecutados

---

## 13. Configuración

### Parámetros de `memory-config.json`

```json
{
  "ramSize": 700,
  "virtualMemorySize": 80,
  "diskSize": 1080
}
```

Los valores por defecto si el archivo no existe: `ramSize=512`, `virtualMemorySize=64`, `diskSize=512`.

### Parámetros de `assembler-config.json`

```json
{
  "maxInstructions": 80
}
```

`maxInstructions=0` desactiva el límite completamente.

---

## 14. Construcción y Compilación

### Con NetBeans (recomendado)

1. Abrir el proyecto `simuladorMiniPC` en NetBeans 21+.
2. Ejecutar `.\setup.ps1` para descargar los JARs de JavaFX 24.
3. Presionar **F6** para compilar y ejecutar.

### Con Ant desde la línea de comandos

```powershell
cd simuladorMiniPC
.\setup.ps1          # Descarga JavaFX si no está
ant run              # Compila y ejecuta
ant jar              # Solo genera el JAR
ant clean            # Limpia el directorio build/
```

### Estructura del classpath en tiempo de ejecución

El archivo `nbproject/project.properties` incluye:

```
run.jvmargs=--module-path ${basedir}/libs/javafx \
            --add-modules javafx.controls,javafx.fxml \
            -Dfile.encoding=UTF-8
```

Los tres JARs requeridos en `libs/javafx/`:
- `javafx-base-24-win.jar`
- `javafx-graphics-24-win.jar`
- `javafx-controls-24-win.jar`

---

## 15. Puntos de Extensión

| Extensión | Cómo implementarla |
|-----------|--------------------|
| Nuevo algoritmo de planificación | Implementar `SchedulingAlgorithm`; agregar caso al enum `Policy` en `SchedulingPolicyManager`; registrar en `setPolicy()` |
| Nuevo tipo de instrucción | Agregar constante a `InstructionType` con su peso; agregar caso en `Assembler.parseLine()` y en `CPU.applyInstruction()` |
| Nuevo tipo de interrupción | Agregar constante a `InterruptType`; manejar en `Kernel.executeTick()` |
| Nuevo dispositivo de E/S | Crear instancia `Device` en `IOManager`; agregar métodos `submit*` y manejar en `Kernel.handleCycleEvent()` |
| SwapManager funcional | Implementar `swapOut()` y `swapIn()` en `SwapManager`; invocar desde `MemoryManager.allocate()` cuando no haya RAM disponible |
| Soporte multi-CPU | Extender `Kernel` para poseer un `List<CPU>`; adaptar `Dispatcher` y `SchedulingPolicyManager` para múltiples unidades |


| Capa | Clases principales | Responsabilidad |
|------|--------------------|-----------------|
| Vista | `SimuladorApp` | Construccion de la UI JavaFX |
| Controlador | `SimuladorController` | Comandos de usuario y sincronizacion con el kernel |
| Modelo | `Kernel` y subsistemas | Estado completo y logica de simulacion |

Subsistemas clave:

- `assembler`: parser y validacion de `.asm`
- `cpu`: ejecucion de instrucciones y `Dispatcher`
- `process`: colas y transiciones de estado
- `memory`: RAM, BCP del SO y memoria virtual
- `io` / `interrupt`: E/S, interrupciones y llamadas al sistema
- `stats`: metricas por tick y por proceso

## 3. Flujo principal de carga y ejecucion

1. `loadFiles()` recibe uno o varios archivos.
2. `Assembler.parse()` valida sintaxis y genera instrucciones con peso.
3. `Kernel.loadProgram()` crea el `PCB`.
4. `MemoryManager.allocate()` intenta asignar RAM.
5. `Kernel.executeTick()` avanza un ciclo completo del sistema.
6. `SchedulingPolicyManager` selecciona el siguiente proceso.
7. `Dispatcher.dispatch()` realiza el cambio efectivo en CPU.
8. `CPU.executeCycle()` procesa la instruccion actual.
9. `Kernel` atiende interrupciones, actualiza colas y notifica a la interfaz.
10. `StatisticsManager` actualiza estadisticas.

## 4. Enfasis funcional implementado

### Carga y validacion

- soporte para multiples archivos `.asm`
- validacion por archivo
- mensajes claros por linea
- descarte aislado de archivos invalidos

### Ejecucion y pesos

- `Paso` ejecuta un tick
- `Auto-todos` ejecuta la carga completa
- cada instruccion posee peso de CPU
- la rafaga del proceso se calcula desde esos pesos

### Interrupciones y llamadas al sistema

- `INT 20H` finaliza
- `INT 10H` produce salida
- `INT 09H` bloquea hasta teclado
- `INT 21H` opera sobre archivos del disco simulado

### Dispatcher y cambios de contexto

- `cpu.Dispatcher` es la pieza responsable del despacho
- `dispatch(PCB, CPU)` carga el proceso entrante
- `preempt(CPU)` retira el proceso actual
- se contabilizan cambios de contexto para estadisticas

### Planificacion

- La interfaz actual se documenta en modo `FCFS`.
- El codigo conserva la infraestructura de `SchedulingPolicyManager` y otras estrategias, pero el flujo visible de esta version se centra en FCFS.

### Memoria virtual, proteccion y seguridad

- RAM con area del SO en `0-19`
- `first-fit` para ubicar procesos
- traduccion virtual mediante `VirtualMemoryManager`
- validacion de offsets para impedir accesos fuera de rango
- fallos de pila e instrucciones invalidas no colapsan todo el sistema

## 5. Interfaz actual y observabilidad

La UI JavaFX actualiza estas vistas:

- izquierda: BCP activo y registro de eventos
- centro: colas verticales con scroll
- derecha: RAM y disco
- divisores arrastrables para redimensionar zonas
- fila de teclado integrada en la consola de eventos

Esto permite verificar directamente:

- proceso en CPU
- registros `IR`, `AC`, `PC` y registros generales
- BCP almacenado en RAM
- lista de trabajos y cambios de estado
- salidas por pantalla
- actividad del disco
- estadisticas finales

## 6. Configuracion

El medio de configuracion del sistema es por archivo:

- `assembler-config.json`: limite de instrucciones
- `memory-config.json`: tamano de RAM, memoria virtual y disco

## 7. Estadisticas finales

Al cierre de la simulacion se registran, por proceso:

- nombre
- hora de inicio
- hora final
- duracion en segundos
- espera
- respuesta
- retorno
- servicio

Ademas se calcula utilizacion de CPU y cambios de contexto globales.

## 8. Archivos tecnicos clave

| Archivo | Rol |
|--------|-----|
| `simuladorminipc/fx/SimuladorApp.java` | Construccion de la interfaz |
| `simuladorminipc/fx/SimuladorController.java` | Enlace entre UI y kernel |
| `simuladorminipc/kernel/Kernel.java` | Orquestacion del tick |
| `simuladorminipc/cpu/Dispatcher.java` | Despacho y retiro de procesos |
| `simuladorminipc/memory/MemoryManager.java` | Asignacion de memoria y area del SO |
| `simuladorminipc/memory/VirtualMemoryManager.java` | Traduccion y validacion de direcciones |
| `simuladorminipc/stats/StatisticsManager.java` | Metricas del sistema y por proceso |
