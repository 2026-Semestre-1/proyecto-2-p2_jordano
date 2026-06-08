# 05 — User Interaction Flow

## 1. Launch

1. `MainFrame.main()` delegates to `SimuladorApp.main()`.
2. JavaFX creates the main window.
3. `SimuladorController` boots the `Kernel` and loads base configuration.
4. The UI becomes ready to load `.asm` files.

## 2. Current layout

- Top bar: `Cargar archivos`, `Auto-todos`, `Por proceso`, `Paso`, `Detener`, `Reiniciar`, `Info`, tick label and `FCFS` badge.
- Left: active BCP on top and event log below.
- Center: `Nueva`, `Lista`, `Bloqueada` and `Terminada` queues stacked vertically with scroll.
- Right: `RAM` and `Disco` tabs.
- All three main regions are resizable by drag.

## 3. Load flow

| Step | User action | System response |
|------|-------------|-----------------|
| 1 | Click `Cargar archivos` | Multi-file chooser opens |
| 2 | Select one or more `.asm` files | File list returns |
| 3 | — | `Assembler.parse()` validates each file |
| 4 | — | Each valid file creates a `PCB` |
| 5 | — | `MemoryManager` attempts RAM placement |
| 6 | — | Log reports clear errors or successful load |

## 4. Manual execution flow

| Step | User action | System response |
|------|-------------|-----------------|
| 1 | Click `Paso` | `kernel.executeTick()` advances one tick |
| 2 | — | Tick counter updates |
| 3 | — | Queues, BCP, RAM, disk and log refresh |

## 5. Automatic execution flow

| Step | User action | System response |
|------|-------------|-----------------|
| 1 | Click `Auto-todos` | Auto-run timeline starts |
| 2 | — | Simulation runs until completion or keyboard wait |
| 3 | Click `Detener` | Auto-run pauses |

## 6. By-process flow

| Step | User action | System response |
|------|-------------|-----------------|
| 1 | Click `Por proceso` | Runs until one process finishes or blocks |
| 2 | — | UI stops automatically at that point |

## 7. Keyboard and interrupts

| Event | Visible result |
|------|-----------------|
| `INT 09H` | Keyboard row is enabled at the bottom of the event log |
| `INT 10H` | Output line is appended to the event log |
| `INT 21H` | Process blocks for disk I/O and later returns to `Lista` |
| `INT 20H` | Process terminates |

## 8. Reset

| Step | User action | System response |
|------|-------------|-----------------|
| 1 | Click `Reiniciar` | Queues, RAM, disk, log and tick are cleared |
| 2 | — | Simulator returns to initial state |
