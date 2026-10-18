# Drum Practice Offline — prototipo Android (Xiaomi 14 Pro)

## Estado real
- Implementado: selector de archivos de audio de Android, reproducción/pausa/detención local sin Internet.
- Incluida: dependencia de ONNX Runtime Android como base para integrar inferencia local.
- **NO implementado**: separación de batería, controles de velocidad, exportación de stems ni APK precompilado.
- No se incluye modelo de IA. No basta con agregar cualquier modelo ONNX: hace falta compatibilidad de entrada/salida, decodificación PCM, ventanas de audio, solapamiento y reconstrucción, y validación de calidad/rendimiento.

## Cómo compilar (cuando tengas una PC)
1. Instalar Android Studio y Android SDK 35.
2. Abrir esta carpeta como proyecto Gradle.
3. Esperar sincronización; conectar Xiaomi con depuración USB o usar APK de prueba.
4. Build > Build APK(s).

## Próximo hito: separación local real
1. Seleccionar modelo de dos stems (drums / no-drums) exportado y probado en ONNX para Android.
2. Incorporar decodificación de MP3 a PCM con MediaExtractor/MediaCodec.
3. Procesar audio por segmentos con overlap-add en un servicio en primer plano.
4. Convertir salida a WAV, reproducirla y medir consumo RAM/tiempo en Xiaomi 14 Pro.
5. Pruebas con temas de 3-6 minutos y comparación auditiva.

Este paquete es código fuente, NO una aplicación APK instalable directamente en el teléfono.

## Versión 0.2 — BPM y metrónomo
- `TempoEngine.kt`: decodifica audio local con MediaExtractor/MediaCodec y estima BPM mediante autocorrelación de cambios de energía; estimación aproximada, puede confundir subdivisiones.
- `ClickMetronome`: clic audible generado con AudioTrack, independiente de la reproducción, con encendido/apagado, volumen, BPM manual y acentos 3/4, 4/4, 6/8.
- El modo 6/8 marca seis pulsos iguales, no agrupación compuesta avanzada.
- **No se ha compilado ni probado en dispositivo.** La separación de batería por IA sigue pendiente.

## Versión 0.3 — reproducción para ensayos
- Control de velocidad de reproducción entre 50% y 150% con `MediaPlayer.playbackParams`.
- Recuerda la URI de la última canción seleccionada, si el proveedor mantiene el permiso de lectura.
- Interfaz desplazable para pantallas móviles.
- Sigue sin incorporar separación IA y sin APK compilado; no se ha probado en dispositivo.

## Versión 0.4 — navegación en la canción
- Barra de progreso para adelantar y retroceder durante la reproducción.
- Tiempo transcurrido y duración total (mm:ss).
- Se conserva la reproducción, velocidad, BPM y metrónomo de v0.3.
- Pendiente: separación real con modelo de IA local, APK compilado y pruebas en teléfono.


## v0.5 — separación espectral local (experimental)
- Dos pistas WAV mono: percusión y acompañamiento, mediante HPSS (medianas espectrales), totalmente offline.
- NO es separación por IA ni aislamiento preciso de batería. La separación real por modelo neuronal sigue pendiente.
- Usa JTransforms 3.1; Gradle requiere Internet una vez para descargar dependencias en la PC, pero la app funciona offline después de instalarse.
- El procesamiento decodifica el tema completo a RAM; temas largos pueden agotar memoria. Los resultados se guardan en archivos internos y se sobrescriben en cada separación.
- No compilado ni probado en Android.
