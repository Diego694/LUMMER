#!/usr/bin/env python3
"""Genera la clave de firma del APK y te dice qué guardar en los secretos de GitHub.

  python scripts/make_keystore.py

Requiere `keytool` (viene con el JDK). Crea ~/registro-academico-signing/ con la clave (.jks) y un archivo
con los valores. NADA de esto se sube al repositorio. Guarda una copia de seguridad: si pierdes esta clave,
nadie podrá publicar actualizaciones que se instalen encima de la app ya instalada.
"""
import base64
import secrets
import shutil
import subprocess
import sys
from pathlib import Path

ALIAS = "registro-academico"
OUT = Path.home() / "registro-academico-signing"


def main() -> int:
    if not shutil.which("keytool"):
        print("✘ No se encontró 'keytool'. Instala un JDK (17+) y vuelve a intentarlo.")
        return 1
    jks = OUT / "release.jks"
    if jks.exists():
        print(f"✘ Ya existe {jks}. Bórrala solo si estás seguro: una clave nueva no puede actualizar instalaciones previas.")
        return 1
    OUT.mkdir(exist_ok=True)
    password = secrets.token_urlsafe(18)
    subprocess.run([
        "keytool", "-genkeypair", "-v", "-keystore", str(jks), "-alias", ALIAS, "-keyalg", "RSA", "-keysize", "2048",
        "-validity", "10000", "-storepass", password, "-keypass", password,
        "-dname", "CN=Lummer, O=Lummer, C=PE",
    ], check=True, capture_output=True)
    b64 = base64.b64encode(jks.read_bytes()).decode()
    (OUT / "secretos-github.txt").write_text(
        f"ANDROID_KEYSTORE_BASE64={b64}\nANDROID_KEYSTORE_PASSWORD={password}\nANDROID_KEY_ALIAS={ALIAS}\nANDROID_KEY_PASSWORD={password}\n",
        encoding="utf-8")
    print(f"""✔ Clave creada en {jks}

Siguiente paso (una sola vez): en GitHub → tu repo → Settings → Secrets and variables → Actions → New repository secret,
crea estos 4 secretos con los valores de  {OUT / 'secretos-github.txt'}:

  ANDROID_KEYSTORE_BASE64
  ANDROID_KEYSTORE_PASSWORD
  ANDROID_KEY_ALIAS
  ANDROID_KEY_PASSWORD

Y haz una copia de seguridad de la carpeta {OUT} en un lugar seguro (no la subas al repositorio).""")
    return 0


if __name__ == "__main__":
    sys.exit(main())
