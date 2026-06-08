; ============================================================
; P3 – Prueba de TODAS las interrupciones
;
; Orden de eventos:
;   1. INT 10H  → pantalla        (NO bloquea, imprime DX)
;   2. INT 21H  → CREAR archivo   (BLOQUEA 5 ticks de disco)
;   3. INT 21H  → ESCRIBIR        (BLOQUEA 5 ticks de disco)
;   4. INT 09H  → teclado         (BLOQUEA indefinido hasta
;                                  que el usuario ingresa 0-255)
;   5. INT 10H  → pantalla        (imprime el valor del teclado)
;   6. INT 21H  → LEER archivo    (BLOQUEA 5 ticks de disco)
;   7. INT 21H  → BORRAR archivo  (BLOQUEA 5 ticks de disco)
;   8. INT 20H  → terminar
; ============================================================

; -- Inicializar registros --
MOV AX, 10
MOV BX, 4
LOAD AX
SUB BX              ; AC = 6
STORE DX            ; DX = 6

; -- INT 10H: imprimir DX (no bloquea) --
INT 10H             ; PANTALLA → "6"

; -- INT 21H subcodigo 60 (0x3C): CREAR archivo (bloquea 5 ticks) --
MOV AH, 60          ; 0x3C = crear archivo
MOV DX, p3data.txt  ; nombre del archivo en DX
INT 21H             ; DISCO CREAR → proceso pasa a BLOQUEADO

; -- INT 21H subcodigo 64 (0x40): ESCRIBIR en archivo (bloquea 5 ticks) --
MOV AL, 99          ; dato a escribir = 99
MOV AH, 64          ; 0x40 = escribir en archivo
MOV DX, p3data.txt
INT 21H             ; DISCO ESCRIBIR → proceso pasa a BLOQUEADO

; -- INT 09H: teclado (bloquea indefinidamente hasta entrada del usuario) --
INT 09H             ; TECLADO → proceso pasa a BLOQUEADO
                    ;   la simulacion pausa; ingresar valor (0-255)
                    ;   en el campo de teclado y pulsar Enviar

; -- El valor ingresado quedo en DX; imprimirlo --
INT 10H             ; PANTALLA → valor que ingreso el usuario

; -- INT 21H subcodigo 77 (0x4D): LEER del archivo (bloquea 5 ticks) --
MOV AH, 77          ; 0x4D = leer archivo
MOV DX, p3data.txt
INT 21H             ; DISCO LEER → proceso pasa a BLOQUEADO

; -- INT 21H subcodigo 65 (0x41): BORRAR archivo (bloquea 5 ticks) --
MOV AH, 65          ; 0x41 = borrar archivo
MOV DX, p3data.txt
INT 21H             ; DISCO BORRAR → proceso pasa a BLOQUEADO

; -- INT 20H: terminar proceso --
INT 20H
