# Diagnóstico OBD — prototipo alfa Android

Aplicación nativa Android (Java), interfaz oscura y conexión Bluetooth clásico RFCOMM para adaptadores ELM327 emparejados.

## Estado
- Prototipo inicial, no validado en vehículos reales.
- Incluye conexión a dispositivos Bluetooth emparejados, inicialización AT básica, sondeo de algunos PID OBD-II genéricos, lectura/borrado básico de DTC y guardado local de nombre de vehículo/perfil.
- El menú de personalización todavía es básico; no es equivalente a los medidores configurables de Torque.
- No incluye todavía equivalencia de combustible completa, sensor de oxígeno 2, gráficos, diseño libre de widgets ni protocolos Renault específicos.
- La detección de protocolo está en modo automático ELM (`ATSP0`); el sondeo de PID se hace de manera secuencial y necesita pruebas/correcciones con respuestas reales de adaptadores distintos.

## Compilar
Abrir la carpeta en Android Studio reciente y sincronizar Gradle. Requiere Android SDK 35 y JDK 17 o compatible. Generar APK desde Build > Build APK(s).

## Limitaciones
La compatibilidad depende del adaptador, del protocolo soportado por la ECU y de los PID que el vehículo exponga. No se debe usar para tomar decisiones de seguridad mientras no se valide en taller.
