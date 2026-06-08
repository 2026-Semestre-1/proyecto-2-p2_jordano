# Manual de Usuario
# Simulador de Gestión de Procesos — Mini PC

---

## 1. Introducción

El **Simulador de Gestión de Procesos Mini PC** es una aplicación educativa que reproduce el comportamiento interno de un sistema operativo cuando gestiona múltiples programas de forma concurrente. Permite cargar programas escritos en un lenguaje ensamblador propio, observar cómo el sistema operativo los carga en memoria, los planifica y los ejecuta instrucción por instrucción, y analizar las métricas de rendimiento al finalizar.

El simulador es ideal para comprender conceptualmente:
- El ciclo de vida de un proceso (NEW → READY → RUNNING → BLOCKED → TERMINATED)
- La asignación de memoria con el algoritmo first-fit
- El funcionamiento de interrupciones del sistema
- Los algoritmos de planificación de CPU (FCFS, Round Robin, SPN, SRT, HRRN, Prioridad)
- Las métricas de rendimiento: tiempo de espera, turnaround, respuesta, utilización de CPU

---

## 2. Requisitos del Sistema

| Requisito | Detalle |
|-----------|---------|
| **Java** | JDK 25 o superior |
| **JavaFX** | 24 (JARs en `simuladorMiniPC/libs/javafx/`) |
| **Sistema operativo** | Windows 10/11 (configuración por defecto); adaptable a Linux/macOS ajustando paths |
| **NetBeans IDE** | 21 o superior (para compilar y ejecutar vía F6) |
| **RAM** | 512 MB disponibles para la JVM |

### Instalación de las librerías JavaFX

Antes de compilar por primera vez en una máquina nueva, ejecute el script de setup desde la raíz del repositorio:

```powershell
.\setup.ps1
```

Este script descarga automáticamente los JARs de JavaFX 24 para Windows desde Maven Central y los coloca en `simuladorMiniPC/libs/javafx/`. NetBeans tomará estas librerías al compilar y ejecutar.

---

## 3. Cómo Iniciar la Aplicación

1. Abra el proyecto `simuladorMiniPC` en NetBeans.
2. Presione **F6** (o clic derecho en el proyecto → *Run*).
3. Se abrirá la ventana principal del simulador.

---

## 4. Descripción de la Interfaz

### 4.1 Barra de Herramientas

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ [Cargar archivos] [Auto-todos] [Por proceso] [Paso] [Detener] [Reiniciar] [Info] │
│                                                              Tick: 0    ● FCFS   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

| Botón | Función |
|-------|---------|
| **Cargar archivos** | Abre un explorador de archivos; permite seleccionar uno o varios archivos `.asm` simultáneamente. |
| **Auto-todos** | Inicia la ejecución automática continua (avanza un tick cada 400 ms) hasta que todos los procesos terminen o un proceso necesite entrada de teclado. Haz clic de nuevo para pausar. |
| **Por proceso** | Ejecuta automáticamente hasta que un proceso adicional alcanza el estado TERMINATED. Útil para ver el ciclo de vida completo de un programa. |
| **Paso** | Ejecuta exactamente un tick del simulador. Ideal para análisis detallado. |
| **Detener** | Pausa la ejecución automática en marcha. |
| **Reiniciar** | Detiene la simulación y vuelve al estado inicial: limpia todas las colas, la RAM, el disco, el registro de eventos y el contador de ticks. Los procesos deben cargarse nuevamente. |
| **Info** | Muestra las estadísticas finales del sistema (utilización de CPU, productividad, tiempos promedio). |
| **Tick: N** | Contador que indica cuántos ciclos de reloj han transcurrido. |
| **FCFS** | Muestra el algoritmo de planificación activo. |

### 4.2 Panel Izquierdo — BCP y Registro de Eventos

El panel izquierdo está dividido verticalmente en dos secciones:

**Sección superior — Estado del proceso en CPU**

Muestra en tiempo real el **Bloque de Control de Proceso (BCP)** del programa que actualmente está siendo ejecutado por la CPU:

| Campo | Descripción |
|-------|-------------|
| Indicador circular | Verde = CPU ocupada; Gris = CPU ociosa |
| PID | Identificador único del proceso en ejecución |
| Estado | Estado actual (RUNNING mientras ejecuta) |
| PC | Contador de programa: índice de la instrucción actual |
| IR | Número de instrucción en ejecución |
| Ráfaga (Burst) | Tiempo total de CPU requerido por el proceso |
| Prioridad | Valor numérico de prioridad (menor = mayor prioridad) |
| AC, AX, BX, CX, DX, AH, AL | Valores actuales de los registros del proceso |

**Sección inferior — Registro de eventos y consola**

Muestra todos los mensajes del sistema en orden cronológico:
- `[LOAD]` — carga de archivos y creación de procesos
- `[INFO]` — transiciones de estado de procesos
- `[SCHED]` — decisiones del planificador y cambios de contexto
- `[PANTALLA]` — salida producida por `INT 10H`
- `[I/O]` — operaciones de archivo por `INT 21H`
- `[KB]` — solicitudes de teclado por `INT 09H`
- `[ERROR]` — errores de ejecución (stack overflow, instrucción inválida)
- `[SIM]` — mensajes del simulador (inicio, fin, reinicio)

Al pie de esta sección aparece la **fila de entrada de teclado**, que se activa cuando un proceso ejecuta `INT 09H`.

### 4.3 Panel Central — Colas de Procesos

Muestra las cuatro colas del sistema, cada una como una tabla independiente con scroll vertical:

| Cola | Color/Título | Procesos que contiene |
|------|--------------|-----------------------|
| **Nueva (NEW)** | Procesos recién cargados; esperan ser admitidos en memoria |
| **Lista (READY)** | Procesos en memoria, listos para ejecutar; esperan su turno de CPU |
| **Bloqueada (BLOCKED)** | Procesos esperando que una operación de E/S se complete |
| **Terminada (TERMINATED)** | Procesos que finalizaron su ejecución; muestran sus tiempos finales |

Cada fila muestra: PID, nombre, estado, prioridad, ráfaga total, tiempo de espera acumulado, PC actual y CPU asignada.

### 4.4 Panel Derecho — Memoria y Disco

Contiene dos pestañas:

**Pestaña RAM**
- Muestra las 700 celdas de la memoria principal.
- Las celdas 0–19 están reservadas para el sistema operativo (area del SO; contienen los BCPs).
- Las celdas 20+ son asignadas a los procesos de usuario mediante first-fit.
- Cada fila indica: dirección, valor almacenado y propietario (PID o "SO").

**Pestaña Disco**
- Muestra las 1080 celdas del disco secundario.
- Las primeras 10 celdas forman el índice de directorio de archivos.
- El resto almacena el contenido de los archivos creados por los procesos.

---

## 5. Cómo Cargar Programas

### Paso a paso

1. Haga clic en **Cargar archivos**.
2. En el explorador que se abre, navegue hasta la carpeta `archivosEnsamblador/` (u otra ubicación).
3. Seleccione uno o varios archivos `.asm`. Para múltiple selección use **Ctrl+clic**.
4. Haga clic en **Abrir**.

### Qué ocurre al cargar

- Cada archivo es analizado por el ensamblador de forma independiente.
- Si el archivo es válido, se crea un proceso (PCB) y aparece en la cola **Nueva**.
- Si el archivo contiene errores, se muestra un mensaje en el registro de eventos con el número de línea y la descripción del problema. **El archivo inválido no bloquea la carga de los demás.**
- Si la RAM tiene espacio suficiente, el proceso pasa automáticamente a la cola **Lista** en el primer tick.

### Archivos de prueba incluidos

| Archivo | Descripción |
|---------|-------------|
| `ejemplo.asm` | Programa de demostración básico |
| `file1.asm` | Proceso de prueba con aritmética |
| `file2.asm` | Proceso con uso de registros |
| `file3_Inter.asm` | Prueba de `INT 10H` (salida a pantalla) |
| `file4_Inter.asm` | Prueba de `INT 21H` (creación y escritura de archivo) |
| `file5_Inter.asm` | Prueba combinada de E/S y archivo |
| `test_err_badasm.asm` | Error: opcode `HALTT` inválido en línea 11 |
| `test_err_badoperands.asm` | Error: cantidad incorrecta de operandos |
| `test_err_stack.asm` | Error: desbordamiento de pila (6 PUSH) |
| `test_err_stackoverflow.asm` | Error: pila vacía al hacer POP |

---

## 6. Modos de Ejecución

### Modo Paso a Paso

Ideal para seguir la simulación en detalle:
1. Cargue uno o más archivos `.asm`.
2. Haga clic en **Paso** repetidamente.
3. Observe cómo los procesos avanzan por las colas, los registros cambian y los eventos se registran.

### Modo Automático (Auto-todos)

Para ver la simulación completa sin intervención manual:
1. Cargue los archivos.
2. Haga clic en **Auto-todos**.
3. La simulación avanza automáticamente (400 ms por tick) hasta que todos los procesos terminan.
4. Si un proceso solicita teclado (`INT 09H`), la ejecución se pausa y espera su entrada.
5. Haga clic en **Detener** en cualquier momento para pausar.

### Modo por Proceso

Para observar el ciclo de vida de un proceso a la vez:
1. Cargue varios archivos.
2. Haga clic en **Por proceso**.
3. La simulación avanza hasta que un proceso adicional alcanza **TERMINATED**.

---

## 7. Lenguaje Ensamblador Soportado

Los programas `.asm` usan un lenguaje simplificado con las siguientes reglas:

### Registros disponibles

| Registro | Descripción |
|----------|-------------|
| `AC` | Acumulador principal |
| `AX` | Registro de propósito general |
| `BX` | Registro de propósito general |
| `CX` | Registro de propósito general (contador) |
| `DX` | Registro de datos (acepta literales de cadena para nombres de archivo) |
| `AH` | Parte alta de AX (subcódigo para `INT 21H`) |
| `AL` | Parte baja de AX (datos para escritura en `INT 21H`) |

### Conjunto de instrucciones

| Instrucción | Sintaxis | Peso (ticks) | Descripción |
|-------------|----------|--------------|-------------|
| `MOV` | `MOV dest, src` | 1 | Copia valor entre registros o asigna literal |
| `LOAD` | `LOAD AC, offset` | 2 | Carga desde RAM[base + offset] en AC |
| `STORE` | `STORE offset, AC` | 2 | Escribe AC en RAM[base + offset] |
| `ADD` | `ADD dest, src` | 3 | `dest = dest + src` |
| `SUB` | `SUB dest, src` | 3 | `dest = dest - src` |
| `INC` | `INC reg` | 1 | `reg = reg + 1` |
| `DEC` | `DEC reg` | 1 | `reg = reg - 1` |
| `SWAP` | `SWAP reg1, reg2` | 1 | Intercambia los valores de dos registros |
| `CMP` | `CMP reg1, reg2` | 2 | Activa `zeroFlag` si `reg1 == reg2` |
| `JMP` | `JMP N` | 2 | Salta incondicionalmente a la instrucción N |
| `JE` | `JE N` | 2 | Salta a la instrucción N si `zeroFlag == true` |
| `JNE` | `JNE N` | 2 | Salta a la instrucción N si `zeroFlag == false` |
| `PUSH` | `PUSH reg` | 1 | Apila el valor del registro (máx. 5 elementos) |
| `POP` | `POP reg` | 1 | Desapila en el registro |
| `PARAM` | `PARAM valor` | 3 | Apila un literal entero |
| `INT 20H` | `INT 20H` | 2 | Termina el proceso normalmente |
| `INT 10H` | `INT 10H` | 2 | Muestra el valor de AC o DX en pantalla |
| `INT 09H` | `INT 09H` | 3 | Solicita entrada del teclado; bloquea el proceso |
| `INT 21H` | `INT 21H` | 5 | Operación de archivo; subcódigo en AH, nombre en DX |

### Subcódigos de INT 21H (registro AH)

| AH | Operación | Descripción |
|----|-----------|-------------|
| 1 | CREATE | Crea un archivo con nombre `DX` en el disco |
| 2 | OPEN | Abre un archivo existente con nombre `DX` |
| 3 | WRITE | Escribe el valor de `AL` en el archivo `DX` |
| 4 | READ | Lee el contenido del archivo `DX` en `AC` |
| 5 | DELETE | Elimina el archivo con nombre `DX` |

### Reglas de sintaxis

- Las líneas que comienzan con `;` son comentarios y se ignoran.
- Las líneas en blanco se ignoran.
- Los nombres de registro no distinguen mayúsculas y minúsculas.
- Los literales de cadena (entre comillas `"texto"`) solo pueden asignarse al registro `DX`.
- El límite máximo de instrucciones por archivo es **80** (configurable en `assembler-config.json`).

### Ejemplo de programa

```asm
; Programa de ejemplo: suma dos valores y los guarda
MOV AX, 10
MOV BX, 20
ADD AX, BX       ; AX = 30
STORE 0, AX      ; Guarda AX en posición virtual 0 de RAM
INT 10H          ; Muestra AC en pantalla
INT 20H          ; Termina el proceso
```

---

## 8. Interrupciones y Llamadas al Sistema

| Instrucción | Comportamiento del proceso | Visible en la UI |
|-------------|---------------------------|------------------|
| `INT 20H` | El proceso termina normalmente; se libera su memoria; se muestran sus estadísticas | Cola Terminada; mensaje `[INFO]` |
| `INT 10H` | Salida: el valor de AC (o el texto en DX) se imprime en el registro de eventos | Mensaje `[PANTALLA]` en el log |
| `INT 09H` | El proceso se bloquea y espera; la UI activa la fila de teclado | Cola Bloqueada; mensaje `[KB]`; campo de texto habilitado |
| `INT 21H` | El proceso se bloquea 5 ticks mientras el disco procesa la operación; luego vuelve a READY | Cola Bloqueada → Lista; mensaje `[I/O]` |

---

## 9. Errores y Mensajes

### Errores durante la carga de archivos

| Mensaje | Causa | Solución |
|---------|-------|----------|
| `Instrucción desconocida: 'XXX'` | Opcode no reconocido | Verificar que el opcode esté en la tabla de instrucciones |
| `Se esperaban N operandos para OPCODE, se encontraron M` | Número incorrecto de operandos | Revisar la sintaxis de la instrucción |
| `Registro inválido 'EAX'` | Registro no reconocido | Usar solo: AC AX BX CX DX AH AL |
| `Los literales de cadena solo pueden asignarse al registro DX` | MOV AX, "texto" | Usar DX como destino para strings |
| `El archivo está vacío o no contiene instrucciones válidas` | Archivo sin instrucciones | Agregar al menos una instrucción válida |
| `El programa excede el límite de 80 instrucciones` | Demasiadas instrucciones | Reducir el programa o cambiar `maxInstructions` en `assembler-config.json` |

### Errores durante la ejecución

| Mensaje | Causa | Efecto |
|---------|-------|--------|
| `Stack overflow en PUSH en línea N` | Pila llena (más de 5 PUSH sin POP) | El proceso termina con error; se libera su memoria |
| `Stack underflow en POP en línea N` | Pila vacía al intentar POP | Igual que el anterior |
| `Tipo de instrucción no manejado: XXX` | Bug interno | Reportar el caso al desarrollador |

---

## 10. Visualización de Estadísticas

Al finalizar la simulación (todos los procesos en TERMINATED) o al hacer clic en **Info**, se muestran las siguientes métricas:

### Métricas globales del sistema

| Métrica | Descripción |
|---------|-------------|
| **Utilización de CPU (%)** | Porcentaje de ticks en que la CPU ejecutó instrucciones vs. total de ticks |
| **Productividad (throughput)** | Número de procesos completados por tick |
| **Tiempo promedio de espera** | Promedio de ticks que los procesos esperaron en cola READY |
| **Tiempo promedio de retorno** | Promedio de ticks desde la llegada hasta la terminación de cada proceso |
| **Tiempo promedio de respuesta** | Promedio de ticks hasta la primera vez que cada proceso obtuvo la CPU |
| **Cambios de contexto** | Número total de veces que el Dispatcher cambió el proceso en CPU |

### Estadísticas por proceso

Cada proceso terminado muestra en su fila de la cola TERMINATED:
- **T. Inicio**: tick en que comenzó a ejecutar por primera vez
- **T. Fin**: tick en que alcanzó TERMINATED
- **T. Espera**: total de ticks en cola READY
- **T. Retorno**: T. Fin − T. Llegada
- **T. Respuesta**: ticks hasta la primera ejecución

---

## 11. Consejos de Uso

- Para comparar algoritmos de planificación, cargue siempre los mismos archivos en el mismo orden y cambie la política entre pruebas.
- Use **Paso** cuando quiera analizar instrucción por instrucción qué hace el planificador.
- Si el simulador queda esperando teclado, escriba cualquier número entero en el campo de entrada y presione el botón `→`.
- El botón **Reiniciar** es seguro: no modifica los archivos `.asm` originales y permite volver a comenzar desde cero sin reiniciar la aplicación.
- Los archivos de la carpeta `archivosEnsamblador/` son sólo de ejemplo: puede crear sus propios programas `.asm` en cualquier carpeta y cargarlos directamente.


## 2. Que puede observar el usuario

- carga de uno o varios archivos `.asm`
- mensajes claros de validacion
- ejecucion por `Paso` o por `Auto-todos`
- BCP activo en ejecucion
- registros `IR`, `AC`, `PC`, `AX`, `BX`, `CX`, `DX`, `AH` y `AL`
- lista de trabajos y cambios de estado
- contenido de RAM y disco
- interrupciones de teclado, pantalla y archivos
- estadisticas finales por proceso

## 3. Interfaz actual

### Barra superior

Controles principales:

- `Cargar archivos`
- `Auto-todos`
- `Por proceso`
- `Paso`
- `Detener`
- `Reiniciar`
- `Info`

La interfaz muestra `FCFS` como politica visible de la version actual.

### Zona izquierda

- Parte superior: BCP activo y registros de CPU.
- Parte inferior: registro de eventos y salida de pantalla.
- Base del chat: ingreso de teclado para `INT 09H`.

### Zona central

- Colas `Nueva`, `Lista`, `Bloqueada` y `Terminada`.
- Se presentan como listas una debajo de otra.
- La seccion tiene scroll vertical.

### Zona derecha

- Pestana `RAM` para ver memoria principal.
- Pestana `Disco` para ver almacenamiento secundario.

### Ajuste de tamano

Las tres zonas principales se pueden redimensionar con click y drag durante la ejecucion.

## 4. Como cargar programas

1. Presione `Cargar archivos`.
2. Seleccione uno o varios archivos `.asm`.
3. El sistema valida cada archivo por separado.
4. Si un archivo contiene errores, se reporta con mensajes claros y no bloquea a los demas.

## 5. Como ejecutar

### Modo manual

- Presione `Paso` para ejecutar un tick del simulador.
- Cada tick representa una unidad discreta de ejecucion de CPU dentro del modelo.

### Modo automatico

- Presione `Auto-todos` para ejecutar toda la carga.
- La ejecucion se detiene al finalizar todos los procesos o cuando un proceso necesita teclado.

### Ejecucion por proceso

- `Por proceso` ejecuta hasta que un proceso termine o quede bloqueado.
