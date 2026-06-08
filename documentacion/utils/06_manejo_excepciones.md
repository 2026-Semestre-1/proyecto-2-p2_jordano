# 06 — Manejo de Excepciones y Casos de Error

---

## 1. Clasificación General de Errores

El simulador maneja errores en tres momentos distintos del ciclo de vida de un programa:

| Fase | Tipo de Error | Manejado en |
|------|--------------|-------------|
| **Análisis estático** (parse) | Errores de sintaxis y semántica del ASM | `Assembler.parse()` → lanza `Exception` → capturada en `SimuladorController.loadFiles()` |
| **Tiempo de ejecución** | Errores durante la ejecución de instrucciones | `CPU.applyInstruction()` → retorna `CycleEvent.error()` → manejado en `Kernel.handleCycleEvent()` |
| **Sistema / infraestructura** | Errores de memoria, estado inválido, configuración | Capturados o ignorados silenciosamente según el subsistema |

---

## 2. Errores de Sintaxis ASM (Parse-Time)

Todos los errores de parser se propagan como `Exception` con un mensaje descriptivo que incluye el número de línea.

### 2.1 — Archivo No Encontrado o Nulo

| Campo | Detalle |
|-------|---------|
| **Cuándo** | Se invoca `parse(null)` o el archivo no existe en el sistema de archivos |
| **Dónde** | Guarda de entrada de `Assembler.parse()` |
| **Excepción** | `Exception("File does not exist: <ruta>")` |
| **Efecto** | El archivo es rechazado; ningún PCB es creado; los demás archivos seleccionados siguen cargando |
| **Visibilidad** | Mensaje en el registro de eventos de la UI |

---

### 2.2 — Opcode Desconocido

| Campo | Detalle |
|-------|---------|
| **Cuándo** | La primera token de una línea no corresponde a ninguna instrucción válida (ej.: `HALTT`, `LOAD2`, `MOV_`) |
| **Dónde** | Caso `default` del switch en `Assembler.parseLine()` |
| **Excepción** | `Exception("[Línea N] Instrucción desconocida: 'HALTT'")` |
| **Archivo de prueba** | `test_err_badasm.asm` — error en línea 11 con opcode `HALTT` |
| **Efecto** | El archivo completo es rechazado |

---

### 2.3 — Operandos Faltantes o en Exceso

| Campo | Detalle |
|-------|---------|
| **Cuándo** | La instrucción requiere N operandos pero se encuentran M ≠ N |
| **Dónde** | `Assembler.splitOperands(raw, expected, lineNum)` |
| **Excepción** | `Exception("[Línea N] Se esperaban <N> operandos para <OPCODE>, se encontraron <M>.")` |
| **Efecto** | El archivo completo es rechazado |

---

### 2.4 — Nombre de Registro Inválido

| Campo | Detalle |
|-------|---------|
| **Cuándo** | Un operando que debe ser un registro contiene un identificador no reconocido (ej.: `EAX`, `R1`, `registro`) |
| **Dónde** | `Assembler.requireRegister(token, lineNum)` |
| **Excepción** | `Exception("[Línea N] Registro inválido 'EAX'. Registros válidos: AC AX BX CX DX AH AL")` |
| **Efecto** | El archivo completo es rechazado |

---

### 2.5 — Código de Interrupción Desconocido

| Campo | Detalle |
|-------|---------|
| **Cuándo** | `INT` seguido de un código diferente a `20H`, `10H`, `09H`, `21H` |
| **Dónde** | Caso `INT` en `Assembler.parseLine()` |
| **Excepción** | `Exception("[Línea N] Código de interrupción desconocido 'XXH'. Válidos: 20H, 10H, 09H, 21H.")` |
| **Efecto** | El archivo completo es rechazado |

---

### 2.6 — Literal de Cadena en Registro Incorrecto

| Campo | Detalle |
|-------|---------|
| **Cuándo** | Se asigna un literal de cadena (`"texto"`) a cualquier registro que no sea `DX` (ej.: `MOV AX, "hola"`) |
| **Dónde** | Caso `MOV` en `Assembler.parseLine()` |
| **Excepción** | `Exception("[Línea N] Los literales de cadena solo pueden asignarse al registro DX.")` |
| **Efecto** | El archivo completo es rechazado |

---

### 2.7 — Archivo Vacío

| Campo | Detalle |
|-------|---------|
| **Cuándo** | El archivo existe pero no contiene ninguna instrucción válida después de eliminar líneas en blanco y comentarios |
| **Dónde** | Verificación post-lectura en `Assembler.parse()` |
| **Excepción** | `Exception("El archivo está vacío o no contiene instrucciones válidas.")` |
| **Efecto** | El archivo es rechazado |

---

### 2.8 — Programa Demasiado Largo (supera `maxInstructions`)

| Campo | Detalle |
|-------|---------|
| **Cuándo** | El número de instrucciones parseadas supera `AssemblerConfig.maxInstructions` (configurable en `assembler-config.json`; por defecto 80; 0 = sin límite) |
| **Dónde** | Verificación post-lectura en `Assembler.parse()` |
| **Excepción** | `Exception("El programa excede el límite de N instrucciones configurado en assembler-config.json.")` |
| **Efecto** | El archivo es rechazado |

---

## 3. Errores de Ejecución en Tiempo de Ejecución (Runtime)

Estos errores son detectados por `CPU.applyInstruction()` durante la simulación. Se comunican al kernel mediante `CycleEvent.error(mensaje)`.

### Flujo general de manejo:
```
CPU.applyInstruction()
    └── lanza excepción Java (StackOverflowError, RuntimeException)
          │
          ▼
    capturada dentro de applyInstruction()
          │
          ▼
    retorna CycleEvent.error("descripción del error")
          │
          ▼
    Kernel.handleCycleEvent(ERROR, process, tick)
          ├── cpu.releaseProcess()
          ├── fireExecutionError(process, mensaje)  → UI: "[ERROR] PID N 'nombre': mensaje"
          ├── statisticsManager.onProcessFinished(process, tick)
          ├── stateManager.terminate(process) → TERMINATED
          └── memoryManager.free(process)
```

---

### 3.1 — Desbordamiento de Pila (Stack Overflow)

| Campo | Detalle |
|-------|---------|
| **Instrucción** | `PUSH reg` o `PARAM valor` |
| **Cuándo** | El puntero de pila `stackPointer >= STACK_SIZE - 1 (4)` (pila llena con 5 elementos) |
| **Dónde** | `RegisterSet.push()` |
| **Excepción Java** | `StackOverflowError("Stack overflow – stack is full (size=5)")` |
| **CycleEvent** | `CycleEvent.error("Stack overflow en PUSH en línea N")` |
| **Archivo de prueba** | `test_err_stack.asm` — el sexto PUSH provoca el error |
| **Efecto** | El proceso termina con error; se libera su memoria; se muestran sus estadísticas parciales |

---

### 3.2 — Subdesbordamiento de Pila (Stack Underflow)

| Campo | Detalle |
|-------|---------|
| **Instrucción** | `POP reg` |
| **Cuándo** | El puntero de pila `stackPointer < 0` (pila vacía) |
| **Dónde** | `RegisterSet.pop()` |
| **Excepción Java** | `RuntimeException("Stack underflow – stack is empty")` |
| **CycleEvent** | `CycleEvent.error("Stack underflow en POP en línea N")` |
| **Archivo de prueba** | `test_err_stackoverflow.asm` |
| **Efecto** | Idéntico al desbordamiento: proceso termina con error |

---

### 3.3 — Tipo de Instrucción No Manejado

| Campo | Detalle |
|-------|---------|
| **Cuándo** | `CPU.applyInstruction()` recibe un `InstructionType` que no existe en su switch-case |
| **Dónde** | Caso `default` en `CPU.applyInstruction()` |
| **CycleEvent** | `CycleEvent.error("Tipo de instrucción no manejado: <tipo>")` |
| **Nota** | No debería ocurrir en operación normal; indicaría un bug interno (nueva instrucción no implementada) |

---

### 3.4 — Contador de Programa Fuera de Rango

| Campo | Detalle |
|-------|---------|
| **Cuándo** | `PC < 0` o `PC >= instrucciones.size()` al inicio de `CPU.executeCycle()` |
| **Dónde** | Guarda de entrada de `CPU.executeCycle()` |
| **CycleEvent** | `CycleEvent.processFinished()` (tratado como finalización natural) |
| **Efecto** | El proceso termina normalmente; no se registra como error |

---

## 4. Errores de Memoria

### 4.1 — RAM Insuficiente para Admitir un Proceso

| Campo | Detalle |
|-------|---------|
| **Cuándo** | `MemoryManager.allocate(PCB)` no encuentra un bloque libre contiguo del tamaño necesario |
| **Dónde** | `MemoryManager.findFreeBlock()` retorna −1 |
| **Retorno** | `false` (sin excepción) |
| **Efecto** | El proceso permanece en cola NEW; `ProcessManager` lo reintentará en el siguiente tick cuando otro proceso libere RAM |
| **Visibilidad** | No hay mensaje de error; el proceso simplemente permanece en la tabla NEW |
| **Recuperación** | Automática: cuando un proceso termina y libera su bloque, el siguiente tick puede admitir el proceso en espera |

---

### 4.2 — Tamaño de RAM Menor al Mínimo

| Campo | Detalle |
|-------|---------|
| **Cuándo** | Se construye `new RAM(size)` con `size <= 20` (menor o igual al área reservada del SO) |
| **Dónde** | Constructor de `RAM` |
| **Excepción Java** | `IllegalArgumentException("RAM size must be greater than OS_RESERVED (20).")` |
| **Efecto** | La JVM no arranca; error fatal al iniciar la aplicación |

---

### 4.3 — Tamaño de Disco Menor al Mínimo

| Campo | Detalle |
|-------|---------|
| **Cuándo** | Se construye `new Disk(size)` con `size <= 10` (menor o igual al área del índice) |
| **Dónde** | Constructor de `Disk` |
| **Excepción Java** | `IllegalArgumentException("Disk size must be > 10")` |
| **Efecto** | Error fatal al iniciar la aplicación |

---

### 4.4 — Dirección de RAM Fuera de Rango

| Campo | Detalle |
|-------|---------|
| **Cuándo** | `ram.read(addr)` o `ram.write(addr, val)` con `addr < 0` o `addr >= size` |
| **Dónde** | `RAM.checkBounds(addr)` |
| **Excepción Java** | `IndexOutOfBoundsException("Invalid RAM address: <addr>. Size: <size>")` |
| **Nota** | No debería ocurrir en operación normal; indicaría un bug en `MemoryManager` o `VirtualMemoryManager` |

---

## 5. Errores de Transición de Estado

### 5.1 — Transición de Estado Inválida

| Campo | Detalle |
|-------|---------|
| **Cuándo** | `StateManager.admitToReady(PCB)` se invoca con un PCB que no está en estado `NEW` |
| **Dónde** | `StateManager.guardTransition(PCB, ProcessState.NEW)` |
| **Excepción Java** | `IllegalStateException("Proceso <pid> debe estar en estado NEW pero está en <estado_actual>")` |
| **Nota** | Solo `admitToReady()` usa `guardTransition()`; las demás transiciones son permisivas |

---

### 5.2 — Envío de Proceso No-NEW

| Campo | Detalle |
|-------|---------|
| **Cuándo** | `ProcessManager.submit(PCB)` es invocado con un proceso que no está en estado `NEW` |
| **Dónde** | Guarda de `ProcessManager.submit()` |
| **Excepción Java** | `IllegalArgumentException("Solo se pueden enviar procesos en estado NEW. PID=<pid>")` |
| **Nota** | No debería ocurrir en uso normal de la API |

---

## 6. Escenarios de Interrupción (No son Errores, pero Requieren Manejo Especial)

### 6.1 — Expiración de Quantum (Round Robin)

| Campo | Detalle |
|-------|---------|
| **Disparador** | `process.quantumRemaining <= 0` al final de un tick, con política Round Robin activa |
| **Flujo** | `interruptManager.raise(QUANTUM_EXPIRED)` → `handleQuantumExpiry()` → `cpu.releaseProcess()` → `stateManager.preemptToReady()` → reinicia `quantumRemaining` |
| **Visibilidad** | Log: `[SCHED] Quantum expirado para 'nombre'`; el proceso vuelve a la tabla READY |

---

### 6.2 — Desbloqueo por Completitud de E/S

| Campo | Detalle |
|-------|---------|
| **Disparador** | `ioManager.tick()` detecta que la cuenta regresiva de un dispositivo llegó a 0 |
| **Flujo** | El proceso es retornado a la lista de desbloqueados → `stateManager.unblockToReady()` → READY |
| **Visibilidad** | Log: `[I/O] 'nombre' desbloqueado`; el proceso reaparece en la tabla READY |

---

### 6.3 — Bloqueo Indefinido por INT 09H

| Campo | Detalle |
|-------|---------|
| **Disparador** | El proceso ejecuta `INT 09H` |
| **Comportamiento** | La solicitud de teclado tiene duración `Integer.MAX_VALUE`; no completa automáticamente |
| **Efecto en el simulador** | Todo `executeTick()` es bloqueado mientras `waitingForKeyboard != null`; el timeline de ejecución automática se detiene |
| **Riesgo** | Si el usuario nunca provee la entrada, la simulación queda suspendida indefinidamente |
| **Recuperación** | El usuario escribe un valor y presiona **Enviar**; el proceso se desbloquea y la simulación continúa |

---

### 6.4 — Contención de Disco (Múltiples Procesos con INT 21H)

| Campo | Detalle |
|-------|---------|
| **Escenario** | Dos procesos ejecutan `INT 21H` en el mismo tick (o en ticks consecutivos con el disco todavía ocupado) |
| **Comportamiento** | El `Device` de disco solo mantiene una `currentRequest`; una nueva asignación sobrescribe la anterior |
| **Limitación** | En la versión actual, el disco no tiene cola de solicitudes pendientes; las solicitudes de E/S no se serializan formalmente |
| **Efecto práctico** | El mapa `waitingProcesses` de `IOManager` sí registra todos los procesos bloqueados; la señal de desbloqueo llega a todos cuando el dispositivo completa su ciclo actual |

---

## 7. Errores de Configuración

### 7.1 — Archivo memory-config.json Ausente o Malformado

| Campo | Detalle |
|-------|---------|
| **Cuándo** | El archivo no está en el classpath, o los valores no pueden ser extraídos por las expresiones regulares |
| **Dónde** | `MemoryConfig.load()` |
| **Comportamiento** | Silencioso; se usan valores por defecto: `RAM=512`, `VMem=64`, `Disco=512` |
| **Visibilidad** | No hay mensaje de error; la simulación arranca normalmente con valores por defecto |

---

### 7.2 — Valores de Configuración por Debajo del Mínimo

| Campo | Detalle |
|-------|---------|
| **Cuándo** | El JSON especifica `ramSize <= 20` o `diskSize <= 10` |
| **Dónde** | Constructores de `RAM` y `Disk` dentro de `MemoryConfig` |
| **Comportamiento** | El valor es elevado al mínimo permitido (RAM=21, Disco=11) silenciosamente mediante `Math.max()` |

---

## 8. Errores de Operaciones de Disco (I/O)

### 8.1 — Archivo No Existe en Lectura/Apertura/Borrado

| Campo | Detalle |
|-------|---------|
| **Cuándo** | `INT 21H` con subcódigo READ (77), OPEN (61) o DELETE (65) referencia un archivo que no existe en el directorio del disco |
| **Dónde** | `Disk.openFile()`, `Disk.readFile()`, `Disk.deleteFile()` |
| **Retorno** | `false` u `null` según el método |
| **Efecto** | La operación es un no-op; el proceso se bloquea de todas formas por 5 ticks y luego se desbloquea normalmente |
| **Nota** | No se genera un evento de error; el proceso reanuda su ejecución como si la operación hubiera tenido éxito |

---

### 8.2 — Archivo Duplicado en CREATE

| Campo | Detalle |
|-------|---------|
| **Cuándo** | `INT 21H` con `ah=60` (CREATE) y el nombre del archivo ya existe en el directorio |
| **Dónde** | `Disk.createFile()` |
| **Retorno** | `false` |
| **Efecto** | Idéntico al caso anterior: no-op con bloqueo y desbloqueo normal |

---

### 8.3 — Disco Lleno en CREATE

| Campo | Detalle |
|-------|---------|
| **Cuándo** | `nextDataAddress >= size` (el área de datos del disco está agotada) |
| **Dónde** | `Disk.createFile()` |
| **Retorno** | `false` |
| **Efecto** | La creación del archivo falla silenciosamente; el proceso bloquea y desbloquea normalmente |

---

## 9. Casos Límite de la Interfaz Gráfica (JavaFX)

### 9.1 — Kernel No Iniciado

| Campo | Detalle |
|-------|---------|
| **Cuándo** | `executeTick()` es invocado antes de `boot()` |
| **Guarda** | `if (!booted \|\| halted) return;` |
| **Efecto** | No-op silencioso |

---

### 9.2 — Ejecución Después de Halt

| Campo | Detalle |
|-------|---------|
| **Cuándo** | `executeTick()` es invocado cuando `halted == true` (todos los procesos terminaron) |
| **Guarda** | Misma guarda de `booted/halted` |
| **Efecto** | No-op; el usuario debe hacer clic en **↺ Reiniciar** o cargar nuevos archivos |

---

### 9.3 — Tick Durante Espera de Teclado

| Campo | Detalle |
|-------|---------|
| **Cuándo** | `executeTick()` es invocado mientras `waitingForKeyboard != null` |
| **Guarda** | `if (waitingForKeyboard != null) return;` |
| **Efecto** | No-op; el timeline de Auto-todos dispara pero no hace nada hasta recibir la entrada del usuario |

---

### 9.4 — Reinicio Durante Ejecución Automática

| Campo | Detalle |
|-------|---------|
| **Cuándo** | El usuario hace clic en **↺ Reiniciar** mientras el timeline está activo |
| **Flujo** | `controller.reset()` invoca primero `stopAuto()` (detiene el timeline) y luego ejecuta el reinicio completo |
| **Efecto** | Estado limpio; sin condición de carrera |

---

### 9.5 — Carga de Nuevos Archivos Después de Halt

| Campo | Detalle |
|-------|---------|
| **Cuándo** | La simulación terminó (`halted=true`); el usuario carga más archivos sin reiniciar |
| **Flujo** | `loadFiles()` invoca `kernel.clearHalt()` internamente → `halted = false` |
| **Efecto** | Los nuevos procesos se agregan sobre la cola de terminados existente; la simulación puede continuar |

---

## 10. Resumen de la Propagación de Errores

```
Error de Sintaxis ASM
    │
    Assembler.parse() lanza Exception
          │
    SimuladorController.loadFiles() la captura
          │
    Agrega al registro de errores
          │
    UI: "[ERROR] archivo.asm: [Línea N] descripción"
          │
    Proceso NO creado; continúa con siguientes archivos


Error de Ejecución en Runtime
    │
    CPU.applyInstruction() captura excepción Java
          │
    Retorna CycleEvent.error("mensaje")
          │
    Kernel.handleCycleEvent(ERROR)
          ├── cpu.releaseProcess()
          ├── stateManager.terminate(PCB) → TERMINATED
          ├── memoryManager.free(PCB)
          ├── statisticsManager.onProcessFinished()
          └── fireExecutionError() → UI: "[ERROR] PID N 'nombre': mensaje"


Error de Memoria (RAM insuficiente)
    │
    MemoryManager.allocate() retorna false
          │
    ProcessManager deja el proceso en cola NEW
          │
    Sin mensaje de error; reintento en siguiente tick
          │
    Recuperación automática cuando se libere RAM
```
