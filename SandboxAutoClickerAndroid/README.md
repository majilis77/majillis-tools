# Sandbox AutoClicker Android 1.1

Proyecto Kotlin basado en el Manual Técnico Sandbox AutoClicker Android.

## Implementado
- Servicio de accesibilidad para ejecutar gestos.
- Captura de pantalla mediante MediaProjection e ImageReader.
- Evaluación periódica de reglas por color.
- Intérprete de reglas IF en español.
- Persistencia local de macros con Room.
- Programación de avisos por hora con AlarmManager.
- Workflow GitHub Actions para compilar APK debug.

## Sintaxis
`SI pixel(540, 1200) == #00FF55 ENTONCES click(540, 1200) ESPERAR 1000`

Las coordenadas son píxeles de pantalla. La comparación ignora el canal alfa y compara RGB.

## Compilar en GitHub
1. Sube el contenido completo de esta carpeta a la raíz de un repositorio.
2. Abre **Actions** y ejecuta **Build Android APK** (también se activa con push a `main`/`master`).
3. Descarga el artefacto `app-debug-apk`, que contiene `app-debug.apk`.

## Permisos y límites importantes
- No necesita root.
- Android solicitará autorización del usuario para capturar la pantalla cada vez que se inicie la captura.
- Activa manualmente el servicio de accesibilidad en Ajustes de Android.
- Las alarmas programadas muestran una notificación para abrir la app y autorizar la captura. Android no permite reutilizar silenciosamente la autorización MediaProjection; por eso no se ejecutan reglas visuales de forma desatendida al sonar la alarma.
- La compilación debe confirmarse ejecutando el workflow en GitHub; no se ha ejecutado un build real en este entorno.
