# Drum Practice v0.7 — Google Colab

Se añadió autorización por cabecera X-Drum-Key y cuaderno de servidor en Colab con túnel temporal HTTPS de Cloudflare.

## Puesta en marcha
1. Entrar en https://colab.research.google.com y subir `DrumPractice_Colab.ipynb`.
2. Ejecutar las celdas en orden. Cuando se pida archivo, subir `server/app.py` de este paquete.
3. Copiar la URL HTTPS y CLAVE al formulario de la app Android v0.7.
4. Colab debe permanecer encendido. No funciona de manera permanente y Google puede desconectar la sesión.

## Advertencias
- No se incluye APK, ni se ha probado el despliegue real en Colab o en Xiaomi.
- No hay garantía de GPU gratuita, disponibilidad o rapidez; CPU puede agotar el tiempo máximo de 15 minutos.
- No compartir la URL ni clave; el tráfico pasa por Cloudflare y Colab. No usar música privada.
- Almacenar una clave en SharedPreferences estándar no es ideal si terceros acceden al móvil; pensado para uso personal.
- Para uso permanente hace falta hosting adecuado (normalmente pago).
