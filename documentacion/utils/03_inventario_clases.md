# 03 — Inventario de Clases

---

## Convenciones

- **Tipo**: tipo Java del atributo o retorno del método
- **Rol**: propósito funcional dentro de la clase
- `→`: la clase posee o usa la clase destino
- `←`: la clase es usada por la clase fuente

---

## 1. `MainFrame`
**Paquete**: `simuladorminipc`  
**Responsabilidad**: Punto de entrada de la JVM. Actúa como adaptador entre el `main` estándar de Java y el arranque de JavaFX.

### Métodos

| Firma | Propósito |
|-------|-----------|
| `static void main(String[] args)` | Invoca `SimuladorApp.main(args)` y retorna. |

### Relaciones
- Invoca → `SimuladorApp`

---

## 2. `Assembler`
**Paquete**: `simuladorminipc.assembler`  
**Responsabilidad**: Analizador léxico y sintáctico estático. Convierte un archivo `.asm` en una lista de instrucciones validadas.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `CONFIG` | `static final AssemblerConfig` | Configuración cargada desde `assembler-config.json`; incluye `maxInstructions` (por defecto 80; 0 = sin límite) |
| `REGISTER_PATTERN` | `static final Pattern` | Expresión regular para validar nombres de registros |
| `INT_PATTERN` | `static final Pattern` | Expresión regular para validar literales enteros |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `static List<Instruction> parse(File file) throws Exception` | Lee el archivo línea por línea, invoca `parseLine()` para cada instrucción y retorna la lista completa. |
| `private static Instruction parseLine(String line, int lineNum) throws Exception` | Tokeniza la línea, identifica el opcode e invoca el sub-parser correspondiente. |
| `private static String[] splitOperands(String raw, int expected, int lineNum) throws Exception` | Divide la cadena de operandos por coma y verifica que el conteo sea el esperado. |
| `private static String requireRegister(String token, int lineNum) throws Exception` | Valida que el token sea un nombre de registro válido. |
| `private static int requireInt(String token, int lineNum) throws Exception` | Valida que el token sea un entero decimal y lo convierte. |
| `private static int parseAhValue(String token, int lineNum) throws Exception` | Parsea el valor del registro AH para instrucciones `INT 21H` (acepta decimal o hexadecimal). |

### Relaciones
- Produce → `List<Instruction>`
- Lanza `Exception` consumida por → `SimuladorController`

---

## 3. `Instruction`
**Paquete**: `simuladorminipc.assembler`  
**Responsabilidad**: Objeto de valor inmutable que representa una instrucción ya analizada sintácticamente y lista para ejecución.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `type` | `InstructionType` | Tipo semántico de la instrucción |
| `operand1` | `String` | Primer operando (registro o literal) |
| `operand2` | `String` | Segundo operando (registro, literal o vacío) |
| `stringLiteral` | `String` | Literal de cadena para `MOV DX, "texto"` |
| `original` | `String` | Línea fuente original, para mensajes de error |
| `weight` | `int` | Número de ticks de CPU que consume la instrucción |
| `lineNumber` | `int` | Número de línea en el archivo fuente |

### Métodos
Constructor con todos los campos; getters sin setters (inmutabilidad).

### Relaciones
- Creada por → `Assembler`
- Almacenada en → `PCB.instructions`
- Leída por → `CPU.applyInstruction()`

---

## 4. `InstructionType`
**Paquete**: `simuladorminipc.assembler`  
**Responsabilidad**: Enumeración de los 19 tipos de instrucción con su peso de ejecución en ticks.

### Valores

| Constante | Peso | Categoría |
|-----------|------|-----------|
| `LOAD` | 2 | Movimiento de datos |
| `STORE` | 2 | Movimiento de datos |
| `MOV` | 1 | Movimiento de datos |
| `ADD` | 3 | Aritmética |
| `SUB` | 3 | Aritmética |
| `INC` | 1 | Aritmética |
| `DEC` | 1 | Aritmética |
| `SWAP` | 1 | Aritmética |
| `INT_20H` | 2 | Interrupción (terminar proceso) |
| `INT_10H` | 2 | Interrupción (salida a pantalla) |
| `INT_09H` | 3 | Interrupción (entrada de teclado) |
| `INT_21H` | 5 | Interrupción (operación de archivo) |
| `JMP` | 2 | Control de flujo |
| `CMP` | 2 | Control de flujo |
| `JE` | 2 | Control de flujo |
| `JNE` | 2 | Control de flujo |
| `PARAM` | 3 | Pila |
| `PUSH` | 1 | Pila |
| `POP` | 1 | Pila |

### Atributos del enum

| Nombre | Tipo | Función |
|--------|------|---------|
| `weight` | `int` | Ticks de CPU requeridos para ejecutar la instrucción |

---

## 5. `SystemClock`
**Paquete**: `simuladorminipc.clock`  
**Responsabilidad**: Reloj global monotónico del simulador.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `currentTick` | `long` | Contador actual de ticks, iniciado en 0 |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `long tick()` | Incrementa `currentTick` en 1 y retorna el nuevo valor. |
| `void reset()` | Reinicia el contador a 0. |
| `long getCurrentTick()` | Retorna el valor actual sin incrementar. |

### Relaciones
- Instanciado en → `Kernel`

---

## 6. `CPU`
**Paquete**: `simuladorminipc.cpu`  
**Responsabilidad**: Unidad de Procesamiento Central. Ejecuta instrucciones de un proceso, respetando los pesos de ciclo, y genera un `CycleEvent` como resultado de cada ciclo.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `id` | `int` | Identificador de la unidad CPU (actualmente siempre 0) |
| `currentProcess` | `PCB` | Proceso actualmente en ejecución; `null` si está ociosa |
| `cycleCountdown` | `int` | Cuenta regresiva de ticks para la instrucción actual |
| `disk` | `Disk` | Referencia al disco para operaciones `INT 21H` |
| `vmm` | `VirtualMemoryManager` | Validador de accesos a memoria virtual |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `CycleEvent executeCycle()` | Ciclo principal: verifica si hay proceso; descuenta peso; invoca `applyInstruction()` cuando llega a cero; retorna el evento resultante. |
| `private CycleEvent applyInstruction(Instruction instr)` | Switch-case sobre `InstructionType`; implementa la semántica de cada instrucción; devuelve `CycleEvent`. |
| `void loadProcess(PCB p)` | Carga el proceso en la CPU; restaura `cycleCountdown` al peso de la instrucción actual. |
| `void releaseProcess()` | Desvincula el proceso actual; CPU queda ociosa. |
| `boolean isIdle()` | Retorna `true` si `currentProcess == null`. |
| `PCB getCurrentProcess()` | Retorna el proceso en ejecución. |
| `int getId()` | Retorna el identificador de la unidad. |

### Relaciones
- Lee instrucciones de → `PCB` (vía `PCB.registers.pc`)
- Modifica registros de → `RegisterSet`
- Invoca operaciones de → `Disk` (para `INT 21H`)
- Valida accesos con → `VirtualMemoryManager`
- Producción de → `CycleEvent` consumido por `Kernel`

---

## 7. `CycleEvent`
**Paquete**: `simuladorminipc.cpu`  
**Responsabilidad**: Objeto de valor inmutable que encapsula el resultado de un ciclo de CPU.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `result` | `CycleResult` | Tipo del resultado |
| `screenText` | `String` | Texto a mostrar en pantalla (`SCREEN_OUTPUT`) |
| `errorMessage` | `String` | Descripción del error (`ERROR`) |
| `fileSubcode` | `int` | Subcódigo de operación de archivo (`FILE_OPERATION`) |
| `filename` | `String` | Nombre del archivo (`FILE_OPERATION`) |

### Métodos (fábricas estáticas)

| Firma | Propósito |
|-------|-----------|
| `static CycleEvent normal()` | Instrucción ejecutada normalmente. |
| `static CycleEvent idle()` | CPU ociosa (sin proceso asignado). |
| `static CycleEvent processFinished()` | El proceso ejecutó `INT 20H` o el PC superó el límite. |
| `static CycleEvent keyboardInput()` | El proceso ejecutó `INT 09H`. |
| `static CycleEvent screenOutput(String text)` | El proceso ejecutó `INT 10H`. |
| `static CycleEvent fileOperation(int subcode, String name)` | El proceso ejecutó `INT 21H`. |
| `static CycleEvent error(String msg)` | Error de ejecución (stack overflow, etc.). |

---

## 8. `CycleResult`
**Paquete**: `simuladorminipc.cpu`  
**Responsabilidad**: Enumeración de los posibles resultados de un ciclo de CPU.

### Valores
`NORMAL`, `PROCESS_FINISHED`, `SCREEN_OUTPUT`, `KEYBOARD_INPUT`, `FILE_OPERATION`, `IDLE`, `ERROR`

---

## 9. `Dispatcher`
**Paquete**: `simuladorminipc.cpu`  
**Responsabilidad**: Gestiona los cambios de contexto formales entre el planificador y la CPU.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `contextSwitches` | `int` | Contador acumulado de cambios de contexto |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `void dispatch(PCB p, CPU cpu)` | Carga el proceso en la CPU y contabiliza el cambio de contexto. |
| `void preempt(CPU cpu)` | Retira el proceso de la CPU sin terminar su ejecución. |
| `int getContextSwitches()` | Retorna el contador de cambios de contexto. |
| `void reset()` | Reinicia el contador a 0. |

### Relaciones
- Invoca → `CPU.loadProcess()` y `CPU.releaseProcess()`
- Instanciado en → `Kernel`

---

## 10. `SimuladorApp`
**Paquete**: `simuladorminipc.fx`  
**Responsabilidad**: Aplicación JavaFX. Construye toda la escena gráfica (grafo de nodos) y la vincula al controlador.

### Atributos principales

| Nombre | Tipo | Función |
|--------|------|---------|
| `ctrl` | `SimuladorController` | Controlador de lógica de presentación |
| `primaryStage` | `Stage` | Ventana principal |
| `logView` | `ListView<String>` | Registro cronológico de eventos |
| `cpuIndicator` | `Circle` | Indicador visual de actividad de CPU |
| `lblCpuStatus` | `Label` | Estado actual de la CPU |
| `lblCpuProcess` | `Label` | Nombre del proceso en ejecución |
| `lblTick` | `Label` | Contador de tick actual |
| `tfKeyboard` | `TextField` | Entrada de teclado para `INT 09H` |
| `btnKeyboard` | `Button` | Envío de entrada de teclado |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `void start(Stage stage)` | Construye el grafo de escena, vincula observables y muestra la ventana. |
| `static void main(String[] args)` | Punto de entrada JavaFX; invocado desde `MainFrame`. |
| Métodos `build*Panel()` | Constructores de la barra superior, panel izquierdo con BCP y eventos, sección central con colas y panel derecho de RAM/Disco. |

### Relaciones
- Posee → `SimuladorController`
- Vincula sus controles a → propiedades observables de `SimuladorController`

---

## 11. `SimuladorController`
**Paquete**: `simuladorminipc.fx`  
**Responsabilidad**: Controlador de presentación. Media entre la interfaz gráfica y el kernel. Implementa `KernelEventListener`. Expone propiedades observables de JavaFX para vinculación bidireccional.

### Atributos principales

| Nombre | Tipo | Función |
|--------|------|---------|
| `kernel` | `Kernel` | Núcleo de simulación poseído por el controlador |
| `autoTimeline` | `Timeline` | Temporizador JavaFX de 400 ms para ejecución automática |
| `newQueueRows` | `ObservableList<ProcessRow>` | Modelo de datos para la tabla NEW |
| `readyQueueRows` | `ObservableList<ProcessRow>` | Modelo de datos para la tabla READY |
| `blockedQueueRows` | `ObservableList<ProcessRow>` | Modelo de datos para la tabla BLOCKED |
| `terminatedQueueRows` | `ObservableList<ProcessRow>` | Modelo de datos para la tabla TERMINATED |
| `memoryRows` | `ObservableList<MemoryRow>` | Modelo de datos para el mapa RAM |
| `diskRows` | `ObservableList<DiskRow>` | Modelo de datos para el mapa de disco |
| `eventLogItems` | `ObservableList<String>` | Entradas del registro de eventos |
| `currentTickProp` | `LongProperty` | Tick actual (vinculado a etiqueta) |
| `cpuStatusProp` | `StringProperty` | Estado CPU (vinculado a etiqueta) |
| `cpuProcessProp` | `StringProperty` | Nombre proceso en CPU (vinculado a etiqueta) |
| `reg*Prop` | `StringProperty` (×9) | Un `StringProperty` por registro del CPU |
| `stat*Prop` | `StringProperty` (×8) | Un `StringProperty` por estadística global |
| `waitingForKeyboardProcess` | `PCB` | Proceso actualmente bloqueado esperando teclado |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `void loadFiles()` | Abre el selector de archivos; invoca `Assembler.parse()`; crea PCBs; los envía al kernel. |
| `void step()` | Invoca `kernel.executeTick()` una vez. |
| `void toggleAuto()` | Inicia o detiene el `autoTimeline`. |
| `void startSingleProcess()` | Ejecuta automáticamente hasta que un proceso adicional llega a TERMINATED. |
| `void reset()` | Detiene el timeline, llama `kernel.reset()`, limpia todos los observables. |
| `void provideKeyboardInput(int value)` | Envía el valor de teclado al kernel; limpia el estado de espera. |
| `void setPolicy(SchedulingPolicyManager.Policy p)` | Mantiene soporte para cambiar la política en el kernel, aunque la barra actual documenta FCFS como política visible. |
| `void setQuantum(int q)` | Mantiene soporte interno para quantum si se reutilizan otras políticas fuera de la UI actual. |
| `void refreshUI()` | Actualiza todos los observables a partir del estado actual del kernel. |
| Métodos `on*` de `KernelEventListener` | Actualizan los observables correspondientes en el hilo de JavaFX mediante `Platform.runLater()`. |

### Clases internas

| Clase | Propósito |
|-------|-----------|
| `ProcessRow` | Clase de datos para las filas de las tablas de colas. Campos: `pid`, `name`, `state`, `priority`, `burst`, `waiting`, `pc`, `cpuId`. |
| `MemoryRow` | Clase de datos para las filas del mapa RAM. Campos: `address`, `value`, `owner`. |
| `DiskRow` | Clase de datos para las filas del mapa de disco. Campos: `address`, `zone`, `value`. |

### Relaciones
- Posee → `Kernel`
- Registrado como → `KernelEventListener`
- Invoca → `Assembler.parse()` durante carga de archivos

---

## 12. `Interrupt`
**Paquete**: `simuladorminipc.interrupt`  
**Responsabilidad**: Objeto de valor inmutable que representa una interrupción generada durante la simulación.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `type` | `InterruptType` | Tipo de interrupción |
| `sourcePCB` | `PCB` | Proceso que generó la interrupción |
| `payload` | `String` | Dato auxiliar (nombre de archivo, texto de pantalla, etc.) |
| `intValue` | `int` | Valor entero auxiliar (subcódigo de archivo, valor de teclado) |

### Relaciones
- Creada por → `InterruptManager.raise()`
- Consumida por → `Kernel.executeTick()`

---

## 13. `InterruptManager`
**Paquete**: `simuladorminipc.interrupt`  
**Responsabilidad**: Cola FIFO de interrupciones pendientes.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `queue` | `ArrayDeque<Interrupt>` | Cola de interrupciones pendientes |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `void raise(InterruptType type, PCB source, String payload, int value)` | Encola una nueva interrupción. |
| `Interrupt poll()` | Extrae y retorna la interrupción al frente; `null` si la cola está vacía. |
| `Interrupt peek()` | Consulta sin extraer la interrupción al frente. |
| `void clear()` | Vacía la cola. |
| `List<Interrupt> snapshot()` | Retorna una copia inmutable para inspección. |

---

## 14. `InterruptType`
**Paquete**: `simuladorminipc.interrupt`  
**Responsabilidad**: Enumeración de los 9 tipos de interrupción del sistema.

### Valores
`PROCESS_FINISHED`, `QUANTUM_EXPIRED`, `IO_REQUEST`, `IO_FINISHED`, `PAGE_FAULT`, `KEYBOARD_INPUT`, `FILE_OPERATION`, `SCREEN_OUTPUT`, `RUNTIME_ERROR`

---

## 15. `Device`
**Paquete**: `simuladorminipc.io`  
**Responsabilidad**: Representa un dispositivo de E/S singular con latencia configurada.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `name` | `String` | Nombre del dispositivo ("TECLADO", "PANTALLA", "DISCO") |
| `latency` | `int` | Ticks requeridos para completar una operación |
| `currentRequest` | `IORequest` | Solicitud actualmente en servicio; `null` si el dispositivo está libre |
| `assignedPid` | `int` | PID del proceso propietario de la solicitud actual |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `void assign(IORequest req, int pid)` | Asigna una solicitud al dispositivo. |
| `void tick()` | Avanza el contador de la solicitud actual. |
| `boolean checkComplete()` | Retorna `true` si la solicitud ha completado su cuenta regresiva. |
| `boolean isBusy()` | Retorna `true` si el dispositivo tiene una solicitud activa. |
| `int getAssignedPid()` | Retorna el PID del proceso en servicio. |
| `void release()` | Libera el dispositivo. |

---

## 16. `IOManager`
**Paquete**: `simuladorminipc.io`  
**Responsabilidad**: Gestiona todos los dispositivos de E/S y el mapa de procesos en espera.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `keyboard` | `Device` | Dispositivo teclado (latencia=3) |
| `screen` | `Device` | Dispositivo pantalla (latencia=1) |
| `disk` | `Device` | Dispositivo disco (latencia=5) |
| `waitingProcesses` | `Map<Integer, PCB>` | Mapa PID → PCB de procesos bloqueados en E/S |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `void submitKeyboardRequest(PCB p)` | Crea solicitud KEYBOARD con duración `MAX_VALUE`; el proceso permanece bloqueado hasta entrada manual. |
| `void completeKeyboardRequest(PCB p, int value)` | Escribe el valor en `RegisterSet.ax`; libera el dispositivo teclado. |
| `void submitFileRequest(PCB p, int subcode, String filename)` | Crea solicitud de archivo sobre el dispositivo disco; latencia=5 ticks. |
| `void submitScreenRequest(PCB p)` | Crea solicitud de pantalla; latencia=1 tick (en la práctica no bloquea al proceso). |
| `List<PCB> tick()` | Avanza un tick en todos los dispositivos; retorna la lista de procesos cuya solicitud completó. |
| `boolean isWaitingForKeyboard()` | Retorna `true` si hay algún proceso esperando teclado. |
| `void removeProcess(PCB p)` | Elimina el proceso del mapa de espera. |
| `void reset()` | Limpia todos los dispositivos y el mapa. |

---

## 17. `Kernel`
**Paquete**: `simuladorminipc.kernel`  
**Responsabilidad**: Núcleo orquestador. Propietario de todos los subsistemas. Implementa el pipeline de simulación de 10 etapas.

### Atributos principales

| Nombre | Tipo | Función |
|--------|------|---------|
| `clock` | `SystemClock` | Reloj global |
| `processManager` | `ProcessManager` | Admisión de procesos desde NEW |
| `queueManager` | `QueueManager` | Gestión de todas las colas |
| `stateManager` | `StateManager` | Transiciones de estado legales |
| `scheduler` | `SchedulingPolicyManager` | Selección del próximo proceso |
| `dispatcher` | `Dispatcher` | Cambios de contexto formales |
| `cpu` | `CPU` | Unidad de ejecución |
| `ioManager` | `IOManager` | Gestión de E/S |
| `interruptManager` | `InterruptManager` | Cola de interrupciones |
| `memoryManager` | `MemoryManager` | Asignación de RAM |
| `vmm` | `VirtualMemoryManager` | Traducción de direcciones virtuales |
| `swapManager` | `SwapManager` | Intercambio de memoria (placeholder) |
| `disk` | `Disk` | Disco secundario |
| `statisticsManager` | `StatisticsManager` | Métricas de rendimiento |
| `listeners` | `List<KernelEventListener>` | Observadores registrados |
| `booted` | `boolean` | `true` tras la primera llamada a `boot()` |
| `halted` | `boolean` | `true` cuando todos los procesos terminaron |
| `waitingForKeyboard` | `PCB` | Proceso esperando entrada de teclado; `null` si no hay |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `void boot()` | Inicializa todos los subsistemas con la configuración de `MemoryConfig`. |
| `void executeTick()` | Pipeline de 10 etapas: tick de reloj → admisión → E/S → planificación → ejecución CPU → manejo de evento → quantum → estadísticas → notificación → verificación de fin. |
| `void loadProcess(PCB p)` | Encola el PCB en el `ProcessManager`; desactiva `halted` si era `true`. |
| `void provideKeyboardInput(int value)` | Desbloquea el proceso esperando teclado; limpia `waitingForKeyboard`. |
| `void reset()` | Restablece todos los subsistemas a su estado inicial. |
| `void addListener(KernelEventListener l)` | Registra un observador. |
| `void setPolicy(Policy p)` | Cambia el algoritmo de planificación. |
| `void setQuantum(int q)` | Configura el quantum de Round Robin. |
| `private void scheduleAndDispatch(long tick)` | Selecciona el próximo proceso con el planificador y lo despacha si la CPU está ociosa o es necesario apropiarlo. |
| `private void handleCycleEvent(CycleEvent ev, PCB process, long tick)` | Maneja cada tipo de `CycleResult` con la acción correspondiente. |
| `private void handleQuantumExpiry(PCB p, long tick)` | Genera interrupción `QUANTUM_EXPIRED`; reacomoda el proceso en la cola READY. |
| `private boolean allFinished()` | Retorna `true` si todas las colas activas están vacías y la CPU está ociosa. |
| Métodos `fire*` | Notifican a los listeners: `fireTickCompleted`, `fireProcessStateChanged`, `fireScreenOutput`, `fireKeyboardInputRequired`, `fireFileOperation`, `fireExecutionError`, `fireAllFinished`. |

---

## 18. `KernelEventListener`
**Paquete**: `simuladorminipc.kernel`  
**Responsabilidad**: Interfaz observadora que desacopla el `Kernel` de la interfaz gráfica.

### Métodos de la interfaz

| Firma | Cuándo se invoca |
|-------|-----------------|
| `void onTickCompleted(long tick)` | Al finalizar cada ciclo de ejecución |
| `void onProcessStateChanged(PCB p, ProcessState prev, ProcessState curr)` | Al cambiar el estado de cualquier proceso |
| `void onScreenOutput(PCB p, String text)` | Al ejecutarse `INT 10H` |
| `void onKeyboardInputRequired(PCB p)` | Al ejecutarse `INT 09H` |
| `void onFileOperation(PCB p, String opName, String filename)` | Al ejecutarse `INT 21H` |
| `void onExecutionError(PCB p, String msg)` | Al producirse un error de ejecución |
| `void onAllFinished()` | Cuando todos los procesos terminaron |

### Relaciones
- Implementada por → `SimuladorController`
- Invocada por → `Kernel`

---

## 19. `RAM`
**Paquete**: `simuladorminipc.memory`  
**Responsabilidad**: Memoria de acceso aleatorio simulada como arreglo de cadenas.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `OS_RESERVED` | `static final int = 20` | Celdas reservadas para el SO (0–19) |
| `size` | `int` | Tamaño total de la RAM |
| `cells` | `String[]` | Contenido de cada celda de memoria |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `String read(int addr)` | Lee el contenido de la celda `addr`. |
| `void write(int addr, String value)` | Escribe `value` en la celda `addr`. |
| `void clearRange(int start, int end)` | Limpia un rango de celdas (escribe `null`). |
| `int userStart()` | Retorna `OS_RESERVED` (20). |
| `int userEnd()` | Retorna `size - 1`. |
| `int getSize()` | Retorna el tamaño total. |
| `String[] getCells()` | Retorna el arreglo completo (para visualización). |
| `void reset()` | Limpia toda la memoria. |
| `private void checkBounds(int addr)` | Valida que `addr` esté en rango; lanza `IndexOutOfBoundsException` de lo contrario. |

---

## 20. `MemoryManager`
**Paquete**: `simuladorminipc.memory`  
**Responsabilidad**: Asignador por primer ajuste (*first-fit*). Gestiona los bloques de la RAM de usuario.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `ram` | `RAM` | Referencia a la RAM física |
| `blocks` | `List<MemoryBlock>` | Lista de todos los bloques (libres y ocupados) |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `boolean allocate(PCB p)` | Busca el primer bloque libre de tamaño suficiente; lo asigna al PCB; actualiza `blocks`. Retorna `false` si no hay espacio. |
| `void free(PCB p)` | Marca el bloque del proceso como libre y limpia sus celdas en RAM. |
| `int freeWords()` | Retorna la suma de tamaños de todos los bloques libres. |
| `void updateBcpOsArea(PCB p)` | Escribe los campos principales del PCB en las celdas 0–19 de la RAM (área del SO). |
| `void storeBcpInOsArea()` | Actualiza el área reservada del SO con el estado actual de todos los procesos activos. |
| `private int findFreeBlock(int size)` | Itera `blocks` y retorna el índice del primer bloque libre con capacidad suficiente; −1 si no hay. |
| `List<MemoryBlock> getBlocks()` | Retorna la lista de bloques (para visualización). |
| `void reset()` | Restablece la lista de bloques y limpia la RAM. |

---

## 21. `MemoryBlock`
**Paquete**: `simuladorminipc.memory`  
**Responsabilidad**: Descriptor de un bloque de memoria contiguo.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `base` | `int` | Dirección física de inicio del bloque |
| `size` | `int` | Número de celdas del bloque |
| `pid` | `int` | PID del proceso propietario; −1 si libre |
| `free` | `boolean` | `true` si el bloque está disponible |

### Métodos
Getters y setters; `getLimit()` retorna `base + size - 1`.

---

## 22. `MemoryConfig`
**Paquete**: `simuladorminipc.memory`  
**Responsabilidad**: Lee y provee los parámetros de configuración del hardware simulado.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `ramSize` | `int` | Tamaño de la RAM en celdas (default 512) |
| `virtualMemorySize` | `int` | Tamaño del segmento de memoria virtual por proceso (default 64) |
| `diskSize` | `int` | Tamaño del disco en celdas (default 512) |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `static MemoryConfig load()` | Lee `memory-config.json` del classpath usando expresiones regulares. Retorna defaults si el archivo no existe o no puede parsearse. |
| `static MemoryConfig defaults()` | Retorna una instancia con los valores por defecto. |

---

## 23. `VirtualMemoryManager`
**Paquete**: `simuladorminipc.memory`  
**Responsabilidad**: Translación de direcciones virtuales a físicas para cada proceso.

### Métodos

| Firma | Propósito |
|-------|-----------|
| `int translate(PCB p, int virtualOffset)` | Retorna `p.memoryBase + virtualOffset`. |
| `boolean isValid(PCB p, int virtualOffset)` | Retorna `true` si `virtualOffset >= 0` y `virtualOffset < p.memoryLimit - p.memoryBase`. |

---

## 24. `SwapManager`
**Paquete**: `simuladorminipc.memory`  
**Responsabilidad**: Marcador de posición para futura funcionalidad de intercambio (swap) entre RAM y almacenamiento secundario. No completamente implementado en la versión actual.

### Métodos

| Firma | Propósito |
|-------|-----------|
| `boolean swapOut(PCB p, MemoryManager mm)` | Debería copiar el bloque del proceso al área de swap y liberarlo de RAM. Actualmente retorna `false`. |
| `boolean swapIn(MemoryManager mm)` | Debería restaurar el bloque desde swap. Actualmente retorna `false`. |

---

## 25. `PCB`
**Paquete**: `simuladorminipc.model`  
**Responsabilidad**: Bloque de Control de Proceso. Estructura central que reúne todos los metadatos de un proceso durante su ciclo de vida.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `pidCounter` | `static AtomicInteger` | Generador atómico de PIDs únicos (autoincremento) |
| `pid` | `int` | Identificador único del proceso |
| `name` | `String` | Nombre del proceso (nombre del archivo sin extensión) |
| `state` | `ProcessState` | Estado actual del proceso |
| `arrivalTime` | `long` | Tick en que el proceso fue admitido |
| `burstTime` | `long` | Suma total de pesos de todas las instrucciones |
| `remainingTime` | `long` | Ticks de CPU que aún le faltan al proceso |
| `executedTime` | `long` | Ticks de CPU consumidos hasta el momento |
| `priority` | `int` | Valor de prioridad (menor = mayor prioridad) |
| `quantumRemaining` | `int` | Ticks de quantum restantes en la vuelta actual (Round Robin) |
| `waitingTime` | `long` | Ticks acumulados en cola READY |
| `turnaroundTime` | `long` | Tiempo total desde llegada hasta finalización |
| `responseTime` | `long` | Ticks hasta la primera vez que obtuvo CPU (−1 hasta entonces) |
| `serviceTime` | `long` | Tiempo real de servicio de CPU |
| `turnaroundRatio` | `double` | `turnaroundTime / burstTime` |
| `memoryRequired` | `int` | Palabras de RAM requeridas (= número de instrucciones) |
| `memoryBase` | `int` | Dirección física de inicio asignada por `MemoryManager` |
| `memoryLimit` | `int` | Dirección física de fin del bloque |
| `registers` | `RegisterSet` | Conjunto de registros de la CPU para este proceso |
| `pendingIORequests` | `Queue<IORequest>` | Cola de solicitudes de E/S pendientes |
| `currentIORequest` | `IORequest` | Solicitud de E/S actualmente en servicio |
| `openFiles` | `Set<String>` | Nombres de archivos abiertos por el proceso |
| `instructions` | `List<Instruction>` | Programa compilado listo para ejecución |
| `cpuId` | `int` | ID de la CPU que ejecuta o ejecutó el proceso |
| `startTime` | `long` | Tick de inicio real (primera vez en RUNNING) |
| `endTime` | `long` | Tick de fin (cuando alcanza TERMINATED) |
| `wallStartMillis` | `long` | Tiempo de pared de inicio en milisegundos |
| `wallEndMillis` | `long` | Tiempo de pared de fin en milisegundos |
| `started` | `boolean` | `true` si el proceso ya obtuvo CPU al menos una vez |
| `finished` | `boolean` | `true` si el proceso ha terminado |
| `nextBCP` | `int` | Puntero al siguiente BCP en el área del SO (lista enlazada virtual) |

### Métodos principales

| Firma | Propósito |
|-------|-----------|
| `static void resetPidCounter()` | Reinicia el generador de PIDs a 1 (usado en `reset()` del kernel). |
| `Instruction getCurrentInstruction()` | Retorna la instrucción apuntada por `registers.pc`. |
| `boolean hasMoreInstructions()` | Retorna `true` si `registers.pc < instructions.size()`. |

---

## 26. `ProcessState`
**Paquete**: `simuladorminipc.model`  
**Responsabilidad**: Enumeración de los 7 estados del proceso.

### Valores

| Constante | `displayName` |
|-----------|--------------|
| `NEW` | "Nuevo" |
| `READY` | "Listo" |
| `RUNNING` | "En ejecución" |
| `BLOCKED` | "Bloqueado" |
| `SUSPENDED_READY` | "Susp. Listo" |
| `SUSPENDED_BLOCKED` | "Susp. Bloqueado" |
| `TERMINATED` | "Terminado" |

---

## 27. `RegisterSet`
**Paquete**: `simuladorminipc.model`  
**Responsabilidad**: Conjunto completo de registros de la CPU para un proceso. Incluye pila de tamaño fijo.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `STACK_SIZE` | `static final int = 5` | Capacidad máxima de la pila |
| `ac` | `int` | Registro acumulador |
| `ax` | `int` | Registro de propósito general AX |
| `bx` | `int` | Registro de propósito general BX |
| `cx` | `int` | Registro de propósito general CX |
| `dx` | `int` | Registro de datos DX |
| `ir` | `int` | Registro de instrucción actual (PC numérico) |
| `pc` | `int` | Contador de programa |
| `ah` | `int` | Parte alta de AX (para subcódigos de INT 21H) |
| `al` | `int` | Parte baja de AX (para datos de escritura en INT 21H) |
| `dxString` | `String` | Parte de cadena de DX (para nombres de archivo en INT 21H) |
| `zeroFlag` | `boolean` | Bandera de cero (resultado de `CMP`) |
| `stack` | `int[]` | Pila de tamaño `STACK_SIZE` |
| `stackPointer` | `int` | Puntero de pila, inicia en −1 |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `void push(int value)` | Empuja valor a la pila; lanza `StackOverflowError` si está llena. |
| `int pop()` | Extrae valor de la pila; lanza `RuntimeException` si está vacía. |
| `RegisterSet copy()` | Copia profunda del conjunto de registros (para guardado de contexto). |
| `void reset()` | Inicializa todos los registros a 0 y la pila a vacía. |

---

## 28. `IORequest`
**Paquete**: `simuladorminipc.model`  
**Responsabilidad**: Solicitud de operación de E/S con cuenta regresiva.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `Operation` | `enum` | `READ`, `WRITE`, `CREATE`, `DELETE`, `KEYBOARD`, `SCREEN` |
| `operation` | `Operation` | Tipo de operación |
| `countdown` | `int` | Ticks restantes para completar |
| `complete` | `boolean` | `true` cuando `countdown <= 0` |
| `filename` | `String` | Nombre del archivo (si aplica) |
| `data` | `String` | Datos para escribir (si aplica) |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `void tick()` | Decrementa `countdown`; activa `complete` cuando llega a 0. |
| `boolean isComplete()` | Retorna `complete`. |

---

## 29. `ProcessManager`
**Paquete**: `simuladorminipc.process`  
**Responsabilidad**: Admisión de procesos al sistema con control de concurrencia máxima.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `MAX_CONCURRENT_PROCESSES` | `static final int = 5` | Límite de procesos activos simultáneos |
| `queueManager` | `QueueManager` | Cola NEW donde se encolan los nuevos procesos |
| `memoryManager` | `MemoryManager` | Para verificar disponibilidad de RAM durante admisión |
| `stateManager` | `StateManager` | Para formalizar la transición NEW → READY |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `void submit(PCB p)` | Verifica que el PCB esté en estado NEW; lo encola en `newQueue`. Lanza `IllegalArgumentException` de lo contrario. |
| `void loadArrivingProcesses(long tick)` | Itera la cola NEW; por cada proceso con `arrivalTime <= tick` y RAM disponible: asigna memoria, transiciona a READY, lo mueve a readyQueue. |
| `boolean allFinished()` | Retorna `true` si todas las colas de procesos activos están vacías. |

---

## 30. `QueueManager`
**Paquete**: `simuladorminipc.process`  
**Responsabilidad**: Almacenamiento centralizado de las cinco colas de procesos.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `newQueue` | `LinkedList<PCB>` | Cola FIFO para procesos recién creados |
| `readyQueue` | `ArrayList<PCB>` | Procesos listos para ejecución |
| `blockedQueue` | `ArrayList<PCB>` | Procesos esperando E/S |
| `suspendedQueue` | `ArrayList<PCB>` | Procesos suspendidos |
| `terminatedQueue` | `ArrayList<PCB>` | Procesos finalizados |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `void admitToNew(PCB p)` | Agrega a `newQueue`. |
| `void moveToReady(PCB p)` | Mueve de cualquier cola a `readyQueue`. |
| `void moveToBlocked(PCB p)` | Mueve a `blockedQueue`. |
| `void moveToSuspended(PCB p)` | Mueve a `suspendedQueue`. |
| `void moveToTerminated(PCB p)` | Mueve a `terminatedQueue`. |
| `void removeFromAll(PCB p)` | Elimina el proceso de todas las colas (para reset o error). |
| `List<PCB> getAllLive()` | Retorna todos los procesos no terminados (para verificación de fin). |
| Getters de cada cola | Retornan copias no modificables de cada cola. |

---

## 31. `StateManager`
**Paquete**: `simuladorminipc.process`  
**Responsabilidad**: Aplicación de transiciones de estado legales con validación.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `queueManager` | `QueueManager` | Para mover procesos entre colas al cambiar de estado |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `void admitToReady(PCB p)` | NEW → READY; usa `guardTransition()` para validar. |
| `void setRunning(PCB p)` | READY → RUNNING. |
| `void preemptToReady(PCB p)` | RUNNING → READY (expiración de quantum o desalojo). |
| `void blockOnIO(PCB p)` | RUNNING → BLOCKED. |
| `void unblockToReady(PCB p)` | BLOCKED → READY (E/S completada). |
| `void terminate(PCB p)` | Cualquier estado activo → TERMINATED. |
| `private void guardTransition(PCB p, ProcessState expected)` | Lanza `IllegalStateException` si el estado actual no es el esperado. |

---

## 32–37. Planificadores (`FCFSScheduler`, `RoundRobinScheduler`, `SPNScheduler`, `SRTScheduler`, `HRRNScheduler`, `PriorityScheduler`)
**Paquete**: `simuladorminipc.scheduler`  
**Todos implementan**: `SchedulingAlgorithm`

### Interface `SchedulingAlgorithm`

| Método | Propósito |
|--------|-----------|
| `PCB selectNextProcess(List<PCB> readyQueue, PCB currentRunning)` | Selecciona el proceso a ejecutar. `currentRunning` puede ser `null`. |
| `String getName()` | Retorna el nombre legible del algoritmo. |

### Lógica de selección

| Clase | Criterio | Apropiativo |
|-------|----------|-------------|
| `FCFSScheduler` | Menor `arrivalTime`; desempate: menor PID | No |
| `RoundRobinScheduler` | Frente de la cola cuando quantum=0 | Sí |
| `SPNScheduler` | Menor `burstTime` | No |
| `SRTScheduler` | Menor `remainingTime`; desaloja si hay uno menor en READY | Sí |
| `HRRNScheduler` | Mayor `(waitingTime + burstTime) / burstTime` | No |
| `PriorityScheduler` | Menor número de `priority` | No |

---

## 38. `SchedulingPolicyManager`
**Paquete**: `simuladorminipc.scheduler`  
**Responsabilidad**: Gestiona la instancia activa del algoritmo de planificación. Expone el enum `Policy` para cambio dinámico.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `activeAlgorithm` | `SchedulingAlgorithm` | Implementación actualmente activa |
| `currentPolicy` | `Policy` | Política enumerada activa |
| `roundRobinQuantum` | `int` | Quantum actual para RR (default 2) |

### Enum interno `Policy`
`FCFS`, `ROUND_ROBIN`, `SPN`, `SRT`, `HRRN`, `PRIORITY`

### Métodos

| Firma | Propósito |
|-------|-----------|
| `void setPolicy(Policy p)` | Instancia el algoritmo correspondiente a la política seleccionada. |
| `PCB selectNext(List<PCB> queue, PCB current)` | Delega en `activeAlgorithm.selectNextProcess()`. |
| `void setRoundRobinQuantum(int q)` | Actualiza el quantum; si la política activa es RR, reinstancia el planificador. |
| `String getAlgorithmName()` | Retorna el nombre del algoritmo activo. |

---

## 39. `StatisticsManager`
**Paquete**: `simuladorminipc.stats`  
**Responsabilidad**: Cálculo y acumulación de métricas de rendimiento del sistema.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `totalTicks` | `long` | Total de ticks transcurridos |
| `activeTicks` | `long` | Ticks en que la CPU estuvo activa |
| `finishedProcesses` | `List<ProcessStats>` | Instantáneas de todos los procesos finalizados |
| `dispatcher` | `Dispatcher` | Referencia para leer cambios de contexto |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `void onTick(long tick)` | Incrementa `totalTicks`; incrementa `activeTicks` si la CPU no estuvo ociosa. |
| `void onProcessFirstRun(PCB p, long tick)` | Registra el `responseTime` del proceso. |
| `void onProcessFinished(PCB p, long tick)` | Calcula `turnaroundTime`, `serviceTime`, etc. y guarda `ProcessStats`. |
| `double getCpuUtilization()` | `activeTicks / totalTicks * 100`. |
| `double getThroughput()` | `finishedProcesses.size() / totalTicks`. |
| `double getAvgWaitingTime()` | Promedio de `waitingTime` de todos los procesos finalizados. |
| `double getAvgTurnaroundTime()` | Promedio de `turnaroundTime`. |
| `double getAvgResponseTime()` | Promedio de `responseTime`. |
| `int getContextSwitches()` | Delega en `dispatcher.getContextSwitches()`. |
| `void reset()` | Reinicia todos los contadores. |

---

## 40. `ProcessStats`
**Paquete**: `simuladorminipc.stats`  
**Responsabilidad**: Objeto de valor inmutable. Instantánea de las estadísticas de un proceso al finalizar.

### Atributos

| Nombre | Tipo |
|--------|------|
| `pid` | `int` |
| `name` | `String` |
| `priority` | `int` |
| `startTime` | `long` |
| `endTime` | `long` |
| `burstTime` | `long` |
| `waitingTime` | `long` |
| `turnaroundTime` | `long` |
| `responseTime` | `long` |
| `serviceTime` | `long` |
| `turnaroundRatio` | `double` |
| `durationMillis` | `long` |

Todos los campos se establecen en el constructor; no hay setters.

---

## 41. `Disk`
**Paquete**: `simuladorminipc.storage`  
**Responsabilidad**: Disco secundario simulado con directorio y área de datos.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `INDEX_SIZE` | `static final int = 10` | Celdas reservadas para el directorio |
| `size` | `int` | Tamaño total del disco |
| `storage` | `String[]` | Arreglo de celdas del disco |
| `directory` | `Map<String, DiskFile>` | Mapa nombre→descriptor de todos los archivos |
| `nextDataAddress` | `int` | Puntero al siguiente espacio libre para datos |

### Métodos

| Firma | Propósito |
|-------|-----------|
| `boolean createFile(String name)` | Crea una entrada en el directorio; retorna `false` si ya existe o el disco está lleno. |
| `boolean openFile(String name)` | Verifica que el archivo exista; retorna `false` de lo contrario. |
| `boolean writeFile(String name, String data)` | Escribe `data` en el bloque del archivo; actualiza `DiskFile.content`. |
| `String readFile(String name)` | Retorna el contenido del archivo; `null` si no existe. |
| `boolean deleteFile(String name)` | Elimina la entrada del directorio y libera el espacio. Retorna `false` si no existe. |
| `void storeProgram(String name, List<Instruction> instrs)` | Almacena el código compilado de un proceso en el disco. |
| `String[] getStorage()` | Retorna el arreglo de celdas para visualización. |
| `Map<String, DiskFile> getDirectory()` | Retorna el directorio completo. |
| `void reset()` | Limpia todas las celdas y el directorio. |

---

## 42. `DiskFile`
**Paquete**: `simuladorminipc.storage`  
**Responsabilidad**: Descriptor de archivo en el disco simulado.

### Atributos

| Nombre | Tipo | Función |
|--------|------|---------|
| `name` | `String` | Nombre del archivo |
| `startAddress` | `int` | Dirección de inicio en el arreglo del disco |
| `size` | `int` | Número de celdas ocupadas |
| `content` | `String` | Contenido actual del archivo |

### Métodos
Getters; `setContent(String c)` actualiza `content` y `size = c.length()`.
