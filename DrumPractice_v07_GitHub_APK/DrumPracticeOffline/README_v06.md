# Drum Practice v0.6 — IA por Internet (prototipo)

El teléfono envía el archivo elegido por HTTPS a **tu propio servidor** FastAPI/Demucs. El servidor ejecuta `htdemucs` y devuelve `drums.wav` y `no_drums.wav`. El teléfono guarda las dos pistas y las reproduce sin Internet después de descargarlas.

## Servidor (PC Linux o máquina en la nube)
- Instalar Python 3.10/3.11, ffmpeg, y PyTorch compatible con el sistema; preparar entorno virtual.
- `pip install -r server/requirements.txt` (puede requerir versiones compatibles de torch/torchaudio).
- `uvicorn app:app --host 127.0.0.1 --port 8000` desde `server/`.
- Configurar un dominio HTTPS mediante proxy inverso y **autenticación/red privada** antes de exponerlo a Internet. El ejemplo no incluye autenticación: **no publicar el endpoint tal cual**.
- La primera ejecución descarga pesos del modelo; el servidor necesita Internet para ello. Requiere CPU potente o GPU y espacio para WAV grandes.

## Android
- Abrir en Android Studio, sincronizar Gradle, compilar APK.
- Escribir la URL HTTPS del servidor y seleccionar un archivo de audio; pulsar 'Separar batería con IA (Internet)'.
- Los resultados aparecen en 'Escuchar sin batería (IA)' y 'Escuchar solo batería (IA)'.
- El modo HPSS local de v0.5 sigue disponible por separado.

## Límites
- **No es un servicio online listo para usar**: necesitás alojar el servidor y configurar HTTPS. No se incluye clave, servidor público ni APK compilado.
- Máximo 35 MB por canción; timeout 10 minutos en el cliente y 15 en el servidor.
- Sin colas, progreso real de Demucs, cancelación, ni gestión de múltiples usuarios. No usar con un servidor abierto al público sin protección, límites y control de costos.
- No probado en un Xiaomi ni compilado en este entorno. Evitá subir música privada a servidores desconocidos.
