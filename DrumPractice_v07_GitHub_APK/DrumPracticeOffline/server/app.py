"""Run behind an HTTPS reverse proxy. Do not expose unauthenticated publicly."""
from fastapi import FastAPI, UploadFile, File, HTTPException, Header
from fastapi.responses import FileResponse
from starlette.background import BackgroundTask
import subprocess, tempfile, pathlib, shutil, zipfile, os, secrets

app = FastAPI()
MAX_BYTES = 35 * 1024 * 1024

@app.post("/separate")
async def separate(file: UploadFile = File(...), x_drum_key: str | None = Header(default=None)):
    key = os.environ.get("DRUM_API_KEY")
    if not key or not x_drum_key or not secrets.compare_digest(key, x_drum_key):
        raise HTTPException(401, "Clave incorrecta")
    work = pathlib.Path(tempfile.mkdtemp(prefix="drum-ai-"))
    try:
        audio = work / "song.mp3"
        size = 0
        with audio.open("wb") as out:
            while chunk := await file.read(1024 * 1024):
                size += len(chunk)
                if size > MAX_BYTES:
                    raise HTTPException(413, "Audio supera 35 MB")
                out.write(chunk)
        if not size:
            raise HTTPException(400, "Audio vacío")
        # Requires ffmpeg and pytorch installed in server environment.
        command = ["python", "-m", "demucs", "--two-stems", "drums", "-n", "htdemucs", "-o", str(work / "out"), str(audio)]
        subprocess.run(command, check=True, timeout=900, capture_output=True)
        stemdir = work / "out" / "htdemucs" / "song"
        archive = work / "stems.zip"
        with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
            for stem in ("drums", "no_drums"):
                stemfile = stemdir / f"{stem}.wav"
                if not stemfile.exists():
                    raise RuntimeError(f"Missing stem {stem}")
                z.write(stemfile, f"{stem}.wav")
        return FileResponse(archive, media_type="application/zip", filename="stems.zip", background=BackgroundTask(shutil.rmtree, work, ignore_errors=True))
    except subprocess.TimeoutExpired:
        shutil.rmtree(work, ignore_errors=True)
        raise HTTPException(504, "Procesamiento excedió el tiempo máximo")
    except HTTPException:
        shutil.rmtree(work, ignore_errors=True)
        raise
    except Exception:
        shutil.rmtree(work, ignore_errors=True)
        raise HTTPException(500, "No se pudo separar el audio")

@app.get("/health")
async def health():
    return {"status": "ok"}
