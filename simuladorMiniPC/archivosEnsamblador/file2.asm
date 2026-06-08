; ============================================================
; Proceso 2 – Operaciones con Pila y PARAM
; Prueba: PUSH, POP, PARAM, MOV, LOAD, STORE, ADD, SUB
; ============================================================

; -- Inicializar valores --
MOV AX, 20
MOV BX, 7
MOV CX, 3

; -- Apilar valores con PUSH --
PUSH AX             ; pila: [20]
PUSH BX             ; pila: [20, 7]
PUSH CX             ; pila: [20, 7, 3]

; -- Pasar parametros a una subrutina simulada --
PARAM 10, 20, 30    ; tres parametros en stack-area

; -- Recuperar valores de la pila --
POP CX              ; CX = 3  (tope)
POP BX              ; BX = 7
POP AX              ; AX = 20

; -- Aritmetica con los valores recuperados --
LOAD AX             ; AC = 20
ADD BX              ; AC = 27
STORE AX            ; AX = 27

LOAD AX             ; AC = 27
SUB CX              ; AC = 24
STORE AX            ; AX = 24

; -- Incrementar y decrementar --
INC AX              ; AX = 25
INC AX              ; AX = 26
DEC BX              ; BX = 6

; -- Comparar y saltar si NO son iguales --
MOV DX, 100
CMP AX, DX          ; AX(26) != DX(100) → zeroFlag = false
JNE +2              ; saltar 2 por ser diferentes
MOV CX, 0           ; (no debe ejecutarse)
MOV AH, 0           ; (este si se ejecuta)

; -- Salto incondicional --
JMP +1              ; saltar la siguiente instruccion
MOV BX, 999         ; (no debe ejecutarse)

; -- Imprimir en pantalla y terminar --
INT 10H
INT 20H