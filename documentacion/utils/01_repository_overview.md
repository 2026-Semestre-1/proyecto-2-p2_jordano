# 01 — Repository Overview

## 1. Summary

`SimuladorMiniPC` is a Java/JavaFX desktop simulator focused on process loading, memory assignment, FCFS scheduling, interrupts, system calls and execution visibility.

## 2. Current feature emphasis

| Topic | Current behaviour |
|------|-------------------|
| Program loading | Multiple `.asm` files can be loaded in one action. |
| Validation | Each file is parsed and validated before admission; errors are reported clearly. |
| Manual execution | `Paso` advances one simulation tick. |
| Automatic execution | `Auto-todos` runs the workload until completion or keyboard wait. |
| Scheduler shown in UI | `FCFS` is the visible active policy in the current UI. |
| Dispatcher | `cpu.Dispatcher` performs the actual CPU hand-off and counts context switches. |
| Virtual memory | Address translation validates process-local accesses. |
| Protection | Stack and runtime faults terminate only the affected process. |
| Visibility | Active BCP, registers, queues, RAM, disk and event log are always visible. |
| Final statistics | Start time, end time and duration are recorded per process. |

## 3. Current UI layout

- Left: active BCP and event log in a vertically resizable split section.
- Center: process queues as vertically stacked lists inside a scrollable area.
- Right: RAM and disk views in tabs.
- The three main sections are resizable at runtime by drag.

## 4. Main flow

1. The user loads one or more `.asm` files.
2. `Assembler.parse()` validates the source and produces `Instruction` objects.
3. `Kernel.loadProgram()` creates a `PCB` and submits it.
4. `MemoryManager` assigns RAM using first fit.
5. `Kernel.executeTick()` advances the simulation.
6. `Dispatcher.dispatch()` loads the selected process into CPU.
7. The UI refreshes queues, registers, RAM, disk and log output.
8. `StatisticsManager` records per-process and global metrics.

## 5. Key modules

| Module | Responsibility |
|-------|----------------|
| `assembler` | Syntax validation and instruction creation |
| `kernel` | Simulation orchestration |
| `cpu` | Instruction execution and dispatching |
| `process` | Queue and state management |
| `memory` | RAM allocation, BCP OS area and virtual translation |
| `io` / `interrupt` | I/O, interrupts and system calls |
| `fx` | JavaFX visualisation and user interaction |

## 6. Configuration files

- `simuladorMiniPC/src/simuladorminipc/assembler/assembler-config.json`
- `simuladorMiniPC/src/simuladorminipc/memory/memory-config.json`

These files provide the current configuration mechanism for instruction limits and hardware sizes.
