; ============================================================
; Proceso 5 - Interrupciones, Aritmetica y Pila
; Prueba: ADD, SUB, INC, DEC, PUSH, POP, INT 09H, INT 10H, INT 20H
; ============================================================

; -- Bloque 1: Aritmetica basica ADD y SUB --
MOV AX, 15          ; AX = 15
MOV BX, 6           ; BX = 6

LOAD AX             ; AC = 15
ADD BX              ; AC = 21  (15 + 6)
STORE AX            ; AX = 21

LOAD AX             ; AC = 21
SUB BX              ; AC = 15  (21 - 6)
STORE CX            ; CX = 15

INC AX              ; AX = 22
INC AX              ; AX = 23
DEC BX              ; BX = 5

; -- Bloque 2: Prueba de la pila (PUSH / POP) --
MOV DX, 100         ; DX = 100
PUSH AX             ; pila: [23]        guardar AX
PUSH BX             ; pila: [23, 5]     guardar BX
PUSH DX             ; pila: [23, 5, 100] guardar DX

; Modificar registros mientras los originales estan en pila
MOV AX, 0
MOV BX, 0
MOV DX, 0

; Restaurar valores desde la pila (orden inverso)
POP DX              ; DX = 100
POP BX              ; BX = 5
POP AX              ; AX = 23

; -- Bloque 3: Mostrar resultado en pantalla (INT 10H imprime DX) --
MOV DX, 23          ; valor a mostrar
MOV AH, 0           ; subcodigo: imprimir DX en pantalla
INT 10H             ; >>> pantalla: 23

; -- Bloque 4: Mas aritmetica sobre los valores recuperados de la pila --
LOAD AX             ; AC = 23
ADD BX              ; AC = 28  (23 + 5)
STORE AX            ; AX = 28

LOAD AX             ; AC = 28
SUB CX              ; AC = 13  (28 - 15)
STORE DX            ; DX = 13

; Mostrar nuevo resultado
MOV AH, 0
INT 10H             ; >>> pantalla: 13

; -- Bloque 5: Pila anidada con resultado intermedio --
PUSH AX             ; pila: [28]
PUSH DX             ; pila: [28, 13]

MOV AX, 50
MOV BX, 20

LOAD AX             ; AC = 50
SUB BX              ; AC = 30  (50 - 20)
STORE AX            ; AX = 30

PUSH AX             ; pila: [28, 13, 30]

POP AX              ; AX = 30  (restaurar ultimo push)
POP DX              ; DX = 13
POP AX              ; AX = 28  (restaurar primer push de este bloque)

; -- Bloque 6: Leer valor del teclado y operar con el --
MOV AH, 1           ; subcodigo: leer teclado
INT 09H             ; <<< entrada: DX = valor ingresado (0-255)

; Usar el valor leido del teclado en una operacion
LOAD DX             ; AC = valor leido
ADD BX              ; AC = valor_leido + 20
STORE AX            ; AX = resultado

; Mostrar resultado en pantalla
MOV DX, AX          ; preparar DX con el resultado
MOV AH, 0
INT 10H             ; >>> pantalla: valor_leido + 20

; -- Terminar proceso --
INT 20H
