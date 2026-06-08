; ============================================================
; test_err_badoperands.asm
; Prueba de error en el uso de operandos de comandos ASM.
; El ensamblador debe rechazar este archivo con error de sintaxis.
; ============================================================

; -- Instruccion valida --
MOV AX, 10
LOAD AX

; -- ERROR en linea 13: ADD requiere un registro, no un numero literal --
ADD 42

; -- Esta linea nunca se procesa --
INT 20H
