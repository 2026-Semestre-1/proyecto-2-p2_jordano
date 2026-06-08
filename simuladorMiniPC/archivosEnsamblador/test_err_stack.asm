; ============================================================
; test_err_stack.asm – Desbordamiento de pila (Stack Overflow)
; Proposito: verificar que el simulador detecta y reporta
;            correctamente un PUSH sobre una pila llena.
;
; La pila del simulador tiene capacidad maxima de 5 entradas
; (RegisterSet.STACK_SIZE = 5).
; Este programa realiza 5 PUSH validos para llenar la pila
; y luego un 6.o PUSH que debe provocar el error:
;   "Stack overflow in PUSH at line N"
; ============================================================

; -- Cargar valores en registros --
MOV AX, 11
MOV BX, 22
MOV CX, 33
MOV DX, 44
MOV AH, 55

; -- PUSH 1/5: pila depth = 1 --
PUSH AX             ; pila: [11]

; -- PUSH 2/5: pila depth = 2 --
PUSH BX             ; pila: [11, 22]

; -- PUSH 3/5: pila depth = 3 --
PUSH CX             ; pila: [11, 22, 33]

; -- PUSH 4/5: pila depth = 4 --
PUSH DX             ; pila: [11, 22, 33, 44]

; -- PUSH 5/5: pila depth = 5  (pila llena) --
PUSH AH             ; pila: [11, 22, 33, 44, 55]  <- capacidad maxima

; -- PUSH 6/5: DESBORDAMIENTO  (debe lanzar error) --
; El simulador debe reportar:
;   "Stack overflow in PUSH at line N"
; y terminar el proceso con estado ERROR.
MOV AL, 99
PUSH AL             ; <<< ERROR ESPERADO: stack overflow

; -- Las siguientes instrucciones NO deben ejecutarse --
INT 10H
INT 20H
