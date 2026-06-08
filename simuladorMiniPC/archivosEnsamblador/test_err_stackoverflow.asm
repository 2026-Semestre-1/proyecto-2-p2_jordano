; ============================================================
; test_err_stackoverflow.asm
; Prueba de desbordamiento de pila (Stack Overflow)
; La pila tiene capacidad maxima de 5 slots.
; Este programa intenta apilar 6 valores -> ERROR en ejecucion.
; ============================================================

; -- Cargar registros --
MOV AX, 10
MOV BX, 20
MOV CX, 30
MOV DX, 40

; -- Llenar la pila hasta el limite (5 slots) --
PUSH AX         ; slot 1
PUSH BX         ; slot 2
PUSH CX         ; slot 3
PUSH DX         ; slot 4
PARAM 99        ; slot 5  (pila llena)

; -- Este PUSH provoca Stack Overflow (slot 6 > max 5) --
PUSH AX         ; ERROR: Stack overflow

; -- Esta instruccion nunca se alcanza --
INT 20H
