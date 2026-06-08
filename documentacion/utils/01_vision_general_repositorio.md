# 01 — Visión General del Repositorio

---

## 1. Descripción del Proyecto

`SimuladorMiniPC` es una aplicación de escritorio desarrollada en **Java 25** con interfaz gráfica **JavaFX 24** que implementa un simulador educativo de sistema operativo de tiempo discreto. El propósito académico es demostrar, de forma visual e interactiva, los mecanismos fundamentales de gestión de procesos que todo sistema operativo real implementa internamente.

El simulador modela un ciclo de ejecución de CPU denominado **tick**: en cada tick el kernel avanza por un pipeline de 10 etapas que abarca desde la admisión de procesos hasta la actualización de estadísticas y la notificación a la interfaz gráfica. El usuario puede avanzar de a un tick, de a un proceso completo, o activar la ejecución automática continua.

### Objetivos de aprendizaje cubiertos

| Concepto | Mecanismo simulado |
|----------|--------------------|
| Multiprogramación | Hasta 5 procesos concurrentes admitidos en memoria simultáneamente |
| Estados de proceso | 7 estados: NEW → READY → RUNNING → BLOCKED → TERMINATED (más SUSPENDED_READY/BLOCKED) |
| Algoritmos de planificación | 6 políticas: FCFS, Round Robin, SPN, SRT, HRRN, Prioridad |
| Gestión de memoria | RAM de 700 celdas, first-fit, área del SO reservada (celdas 0–19) |
| Memoria virtual | Traducción dirección virtual → física por proceso; validación de offsets |
| Interrupciones | INT 20H, INT 10H, INT 09H, INT 21H — cada una con semántica propia |
| Cambios de contexto | Dispatcher contabiliza y gestiona el guardado/restauración de registros |
| Dispositivos de E/S | Teclado, pantalla y disco simulados con latencias configurables |
| Estadísticas de rendimiento | Utilización de CPU, productividad, tiempos de espera, retorno y respuesta |

---

## 2. Pila Tecnológica

| Componente | Versión | Rol |
|------------|---------|-----|
| **Java** | 25 | Lenguaje principal; gestión de memoria y concurrencia de la JVM |
| **JavaFX** | 24 | Framework de interfaz gráfica (Scene Graph, bindings, Timeline) |
| **NetBeans Ant** | — | Sistema de construcción (build.xml, nbproject/) |
| **JSON manual** | — | Configuración de hardware simulado (regex parser propio, sin dependencias externas) |
| **PlantUML** | — | Diagramas UML del proyecto (en `documentacion/diagramas/`) |

El proyecto no utiliza ninguna dependencia de terceros en tiempo de ejecución aparte de las librerías de JavaFX. Los JARs de JavaFX se descargan localmente mediante `setup.ps1` y se excluyen del repositorio vía `.gitignore`.

---

## 3. Arquitectura General

El sistema sigue un patrón **MVC** con extensiones del patrón **Observer** para desacoplar el kernel de la interfaz gráfica:

```
┌──────────────────────────────────────────────────────────────────────┐
│  CAPA DE PRESENTACIÓN (JavaFX)                                       │
│                                                                      │
│  SimuladorApp ──── construye y vincula ──── SimuladorController      │
│  (Vista: Scene Graph)                       (Controlador: MVP)       │
│                         │                                            │
│                         │ implementa KernelEventListener             │
└─────────────────────────┼────────────────────────────────────────────┘
                          │  posee / invoca
┌─────────────────────────▼────────────────────────────────────────────┐
│  CAPA DE KERNEL (Fachada)                                            │
│                                                                      │
│                       Kernel                                         │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────┬─────────┐ │
│  │assembler │  clock   │   cpu    │ interrupt│    io    │ storage │ │
│  ├──────────┼──────────┼──────────┼──────────┼──────────┼─────────┤ │
│  │ memory   │  model   │ process  │scheduler │  stats   │         │ │
│  └──────────┴──────────┴──────────┴──────────┴──────────┴─────────┘ │
└──────────────────────────────────────────────────────────────────────┘
```

### Patrones de diseño aplicados

| Patrón | Implementación |
|--------|----------------|
| **MVC** | `SimuladorApp` (View) + `SimuladorController` (Controller) + `Kernel` + subsistemas (Model) |
| **Observer** | `KernelEventListener` — interfaz con 7 callbacks; `SimuladorController` la implementa |
| **Strategy** | `SchedulingAlgorithm` — interfaz implementada por 6 planificadores intercambiables |
| **Facade** | `Kernel` oculta la complejidad de 14 subsistemas tras una API uniforme |
| **Value Object** | `Instruction`, `CycleEvent`, `ProcessStats`, `Interrupt`, `DiskFile` — inmutables |
| **Factory Method** | `CycleEvent.normal()`, `.error()`, `.screenOutput()`, etc. — fábricas estáticas |
| **Command** | Botones de la toolbar invocan métodos del controlador (`step()`, `toggleAuto()`, etc.) |

---

## 4. Estructura del Repositorio

```
SimuladorOS/
├── README.md
├── setup.ps1                        ← Script PowerShell: descarga JARs de JavaFX 24
├── .gitignore                       ← Excluye simuladorMiniPC/libs/
├── documentacion/
│   ├── 01_vision_general_repositorio.md      ← Este archivo
│   ├── 02_inventario_paquetes.md             ← Descripción de los 14 paquetes
│   ├── 03_inventario_clases.md               ← 42 clases con atributos y métodos
│   ├── 04_flujo_ejecucion.md                 ← Pipeline de 10 etapas del kernel
│   ├── 05_flujo_usuario.md                   ← Interacción usuario–sistema
│   ├── 06_manejo_excepciones.md              ← Errores en parse, runtime y memoria
│   ├── manual_usuario.md                     ← Guía de uso para el usuario final
│   ├── manual_tecnico.md                     ← Guía técnica para desarrolladores
│   ├── Documentacion_Final_Simulador.html    ← Compilación HTML completa
│   └── diagramas/
│       ├── diagrama_paquetes.puml
│       ├── diagrama_clases_completo.puml
│       ├── diagrama_secuencia_ejecucion.puml
│       └── diagrama_flujo_excepciones.puml
└── simuladorMiniPC/
    ├── build.xml                    ← Script Ant principal
    ├── manifest.mf
    ├── nbproject/                   ← Configuración NetBeans
    ├── archivosEnsamblador/         ← Programas .asm de prueba (8 archivos)
    └── src/simuladorminipc/
        ├── MainFrame.java
        ├── assembler/   (4 archivos + assembler-config.json)
        ├── clock/       (1 archivo)
        ├── cpu/         (4 archivos)
        ├── fx/          (2 archivos)
        ├── interrupt/   (3 archivos)
        ├── io/          (2 archivos)
        ├── kernel/      (2 archivos)
        ├── memory/      (5 archivos + memory-config.json)
        ├── model/       (4 archivos)
        ├── process/     (3 archivos)
        ├── scheduler/   (8 archivos)
        ├── stats/       (2 archivos)
        └── storage/     (2 archivos)
```

---

## 5. Mapa de Paquetes y Responsabilidades

```
simuladorminipc (raíz)          ← Punto de entrada JVM: MainFrame.main()
├── assembler                   ← Parser léxico/sintáctico del lenguaje ASM propio
├── clock                       ← Reloj global monotónico (ticks de simulación)
├── cpu                         ← CPU virtual: fetch-decode-execute + Dispatcher
├── fx                          ← Capa de presentación JavaFX (View + Controller)
├── interrupt                   ← Cola FIFO de interrupciones del kernel
├── io                          ← Dispositivos de E/S con latencias (teclado, pantalla, disco)
├── kernel                      ← Núcleo orquestador (Fachada): pipeline de 10 etapas
├── memory                      ← RAM, first-fit, traducción virtual, área del SO
├── model                       ← Entidades: PCB, RegisterSet, ProcessState, IORequest
├── process                     ← Admisión, colas y transiciones de estado de procesos
├── scheduler                   ← 6 algoritmos de planificación (patrón Strategy)
├── stats                       ← Métricas de rendimiento por tick y por proceso
└── storage                     ← Disco secundario simulado con directorio de archivos
```

**Total: 14 paquetes, 42 clases Java.**

---

## 6. Configuración del Hardware Simulado

Los parámetros de hardware se leen en tiempo de inicio del kernel desde dos archivos JSON ubicados en el classpath bajo `src/simuladorminipc/`:

### `memory-config.json`

```json
{
  "ramSize": 700,
  "virtualMemorySize": 80,
  "diskSize": 1080
}
```

| Parámetro | Valor | Significado |
|-----------|-------|-------------|
| `ramSize` | 700 | Celdas totales de RAM; las primeras 20 (índices 0–19) están reservadas para el SO |
| `virtualMemorySize` | 80 | Tamaño máximo del segmento de memoria virtual por proceso |
| `diskSize` | 1080 | Celdas totales del disco; las primeras 10 forman el directorio |

### `assembler-config.json`

```json
{
  "maxInstructions": 80
}
```

| Parámetro | Valor | Significado |
|-----------|-------|-------------|
| `maxInstructions` | 80 | Límite de instrucciones por archivo `.asm`; 0 = sin límite |

---

## 7. Conjunto de Instrucciones ASM

El lenguaje ensamblador propio soporta **19 tipos de instrucción** agrupados en cinco categorías. Cada instrucción tiene un **peso** que determina cuántos ticks de CPU consume:

| Categoría | Instrucciones | Pesos |
|-----------|--------------|-------|
| Movimiento de datos | `LOAD`, `STORE`, `MOV` | 2, 2, 1 |
| Aritmética | `ADD`, `SUB`, `INC`, `DEC`, `SWAP` | 3, 3, 1, 1, 1 |
| Control de flujo | `JMP`, `CMP`, `JE`, `JNE` | 2, 2, 2, 2 |
| Pila | `PUSH`, `POP`, `PARAM` | 1, 1, 3 |
| Interrupciones | `INT 20H`, `INT 10H`, `INT 09H`, `INT 21H` | 2, 2, 3, 5 |

**Registros disponibles**: `AC`, `AX`, `BX`, `CX`, `DX`, `AH`, `AL`  
**Pila**: tamaño fijo de 5 elementos por proceso; desbordamiento genera error aislado.

---

## 8. Flujo Funcional de Alto Nivel

```
Usuario selecciona .asm(s)
         │
         ▼
SimuladorController.loadFiles()
         │
         ├──► Assembler.parse()  →  List<Instruction>  →  PCB creado
         │
         ▼
Kernel.loadProcess(PCB)  →  QueueManager.admitToNew(PCB)
         │
         ▼  [cada Paso o Auto-todos]
Kernel.executeTick()
  ①  clock.tick()
  ②  ProcessManager.loadArrivingProcesses()  →  NEW → READY (si RAM disponible)
  ③  IOManager.tick()  →  desbloquea procesos con E/S completada
  ④  SchedulingPolicyManager.selectNext()  +  Dispatcher.dispatch()
  ⑤  CPU.executeCycle()  →  CycleEvent
  ⑥  Kernel.handleCycleEvent()  →  interrupciones, E/S, terminación
  ⑦  Verificación de quantum (Round Robin)
  ⑧  StatisticsManager.onTick()
  ⑨  fireTickCompleted()  →  SimuladorController.onTickCompleted()  →  UI
  ⑩  allFinished()?  →  halted = true
```

---

## 9. Interfaz Gráfica

La ventana principal (1440 × 860 px) se divide en tres columnas redimensionables mediante `SplitPane`:

```
┌─────────────────────┬──────────────────────────┬──────────────────┐
│ PANEL IZQUIERDO     │ PANEL CENTRAL            │ PANEL DERECHO    │
│ (SplitPane vertical)│ (ScrollPane + VBox)      │ (TabPane)        │
│                     │                          │                  │
│  ┌───────────────┐  │  ┌────────────────────┐  │  ┌────────────┐  │
│  │  BCP activo   │  │  │  Cola Nueva        │  │  │  RAM       │  │
│  │  + registros  │  │  ├────────────────────┤  │  ├────────────┤  │
│  │  CPU          │  │  │  Cola Lista/Ready  │  │  │  Disco     │  │
│  ├───────────────┤  │  ├────────────────────┤  │  └────────────┘  │
│  │  Registro de  │  │  │  Cola Bloqueada    │  │                  │
│  │  eventos      │  │  ├────────────────────┤  │                  │
│  │  + teclado    │  │  │  Cola Terminada    │  │                  │
│  └───────────────┘  │  └────────────────────┘  │                  │
└─────────────────────┴──────────────────────────┴──────────────────┘
```

**Barra superior**: `Cargar archivos` · `Auto-todos` · `Por proceso` · `Paso` · `Detener` · `Reiniciar` · `Info` · Contador de tick · Etiqueta de política activa (`FCFS`)

---

## 10. Archivos de Prueba Incluidos

El directorio `archivosEnsamblador/` contiene 8 programas `.asm` para validación:

| Archivo | Propósito |
|---------|-----------|
| `ejemplo.asm` | Programa de demostración básico |
| `file1.asm` | Proceso de prueba 1 |
| `file2.asm` | Proceso de prueba 2 |
| `file3_Inter.asm` | Prueba de interrupciones |
| `file4_Inter.asm` | Prueba de E/S con archivos |
| `file5_Inter.asm` | Prueba combinada de E/S |
| `test_err_badasm.asm` | Error de opcode inválido (`HALTT` en línea 11) |
| `test_err_badoperands.asm` | Error de operandos incorrectos |
| `test_err_stack.asm` | Desbordamiento de pila (6 PUSH seguidos) |
| `test_err_stackoverflow.asm` | Stack underflow (POP con pila vacía) |
