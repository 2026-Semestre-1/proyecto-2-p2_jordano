; ============================================================
; test_err_badasm.asm
; Prueba de error de sintaxis ASM detectado al cargar el archivo.
; El ensamblador debe rechazar este archivo y mostrar el error.
; ============================================================

; -- Instruccion valida --
MOV AX, 5

; -- ERROR en linea 12: instruccion desconocida "HALTT" --
HALTT

; -- Esta linea nunca se procesa --
INT 20H
