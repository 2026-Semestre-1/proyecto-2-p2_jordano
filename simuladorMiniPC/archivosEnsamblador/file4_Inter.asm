; ============================================================
; P4 – Prueba de TODAS las interrupciones (orden alternativo)
;
; Orden de eventos:
;   1. INT 21H  → CREAR archivo   (BLOQUEA 5 ticks de disco)
;   2. INT 21H  → ABRIR archivo   (BLOQUEA 5 ticks de disco)
;   3. INT 10H  → pantalla        (NO bloquea, imprime DX)
;   4. INT 09H  → teclado         (BLOQUEA indefinido hasta
;                                  que el usuario ingresa 0-255)
;   5. INT 10H  → pantalla        (imprime valor del teclado + 1)
;   6. INT 21H  → ESCRIBIR        (BLOQUEA 5 ticks de disco)
;   7. INT 21H  → BORRAR archivo  (BLOQUEA 5 ticks de disco)
;   8. INT 20H  → terminar
; ============================================================

; -- Inicializar registros --
MOV AX, 20
MOV BX, 5
LOAD AX
ADD BX              ; AC = 25
STORE CX            ; CX = 25

; -- INT 21H subcodigo 60 (0x3C): CREAR archivo (bloquea 5 ticks) --
MOV AH, 60          ; 0x3C = crear archivo
MOV DX, p4data.txt  ; nombre del archivo en DX
INT 21H             ; DISCO CREAR → proceso pasa a BLOQUEADO

; -- INT 21H subcodigo 61 (0x3D): ABRIR archivo (bloquea 5 ticks) --
MOV AH, 61          ; 0x3D = abrir archivo
MOV DX, p4data.txt
INT 21H             ; DISCO ABRIR → proceso pasa a BLOQUEADO

; -- INT 10H: imprimir CX en pantalla (no bloquea) --
LOAD CX
STORE DX            ; DX = 25
INT 10H             ; PANTALLA → "25"

; -- INT 09H: teclado (bloquea indefinidamente hasta entrada del usuario) --
INT 09H             ; TECLADO → proceso pasa a BLOQUEADO
                    ;   la simulacion pausa; ingresar valor (0-255)
                    ;   en el campo de teclado y pulsar Enviar

; -- Sumar 1 al valor del teclado (que llego en DX) e imprimirlo --
LOAD DX
INC                 ; AC = DX + 1  (INC sin operando incrementa AC)
STORE DX            ; DX = valor + 1
INT 10H             ; PANTALLA → valor ingresado + 1

; -- INT 21H subcodigo 64 (0x40): ESCRIBIR en archivo (bloquea 5 ticks) --
MOV AL, 42          ; dato a escribir = 42
MOV AH, 64          ; 0x40 = escribir en archivo
MOV DX, p4data.txt
INT 21H             ; DISCO ESCRIBIR → proceso pasa a BLOQUEADO

; -- INT 21H subcodigo 65 (0x41): BORRAR archivo (bloquea 5 ticks) --
MOV AH, 65          ; 0x41 = borrar archivo
MOV DX, p4data.txt
INT 21H             ; DISCO BORRAR → proceso pasa a BLOQUEADO

; -- INT 20H: terminar proceso --
INT 20H
