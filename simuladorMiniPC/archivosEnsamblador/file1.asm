; ============================================================
; Proceso 1 – Aritmetica y Control de Flujo
; Prueba: MOV, LOAD, STORE, ADD, SUB, INC, DEC, CMP, JE, JNE, JMP
; ============================================================

; -- Inicializar registros --
MOV AX, 10          ; AX = 10
MOV BX, 4           ; BX = 4
MOV CX, 0           ; CX = 0  (contador de iteraciones)

; -- Cargar AX en AC y sumar BX --
LOAD AX             ; AC = 10
ADD BX              ; AC = 14
STORE AX            ; AX = 14

; -- Decrementar BX y verificar con CMP --
DEC BX              ; BX = 3
DEC BX              ; BX = 2
INC CX              ; CX = 1
INC CX              ; CX = 2

; -- Subtraer y almacenar --
LOAD AX             ; AC = 14
SUB BX              ; AC = 12
STORE AX            ; AX = 12

; -- Usar SWAP --
MOV DX, 99
SWAP AX, DX         ; AX <-> DX  => AX=99, DX=12
SWAP AX, DX         ; restaurar => AX=12, DX=99

; -- Comparacion y salto condicional --
MOV AH, 1
MOV AL, 1
CMP AH, AL          ; zeroFlag = true si son iguales
JE  +2              ; si iguales saltar 2 instrucciones adelante
MOV CX, 99          ; (no debe ejecutarse)
MOV BX, 50          ; (no debe ejecutarse)

; -- Instruccion de pila --
PUSH AX             ; apilar AX=12
MOV AX, 77
POP AX              ; restaurar AX=12

; -- Mostrar resultado en pantalla --
MOV AH, 0           ; subcodigo pantalla
INT 10H             ; imprime DX en pantalla

; -- Terminar proceso --
INT 20H