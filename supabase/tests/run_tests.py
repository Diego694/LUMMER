#!/usr/bin/env python3
"""Ejecuta las migraciones SQL y las pruebas de seguridad (RLS) en un PostgreSQL local y desechable.

  python supabase/tests/run_tests.py [ruta/a/pgsql]

Busca PostgreSQL en: argumento, variable PGBIN, C:\\Users\\<usuario>\\android-build\\pgsql\\bin, o el PATH (en CI: apt/servicio postgres).
No toca ninguna base real: crea un clúster temporal en una carpeta temporal y lo borra al terminar.
"""
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parent
PUERTO = "54329"


def buscar_bin():
    candidatos = [sys.argv[1] if len(sys.argv) > 1 else None, os.environ.get("PGBIN"),
                  str(Path.home() / "android-build" / "pgsql" / "bin")]
    for c in candidatos:
        if c and (Path(c) / ("initdb.exe" if os.name == "nt" else "initdb")).exists():
            return Path(c)
    ruta = shutil.which("initdb")
    if ruta:
        return Path(ruta).parent
    # Debian/Ubuntu: /usr/lib/postgresql/<v>/bin
    for v in sorted(Path("/usr/lib/postgresql").glob("*/bin"), reverse=True) if Path("/usr/lib/postgresql").exists() else []:
        return v
    sys.exit("No se encontró PostgreSQL (initdb). Pasa la ruta como argumento o define PGBIN.")


def main():
    bin_ = buscar_bin()
    exe = ".exe" if os.name == "nt" else ""
    datos = Path(tempfile.mkdtemp(prefix="pg_ra_"))
    env = {**os.environ, "PGPASSWORD": "x"}
    estado = 1
    try:
        subprocess.run([bin_ / f"initdb{exe}", "-D", datos, "-U", "postgres", "-A", "trust", "-E", "UTF8"], check=True, capture_output=True)
        # stdout/stderr a DEVNULL: si se capturan, el servidor hereda las tuberías y subprocess.run espera para siempre.
        subprocess.run([bin_ / f"pg_ctl{exe}", "-D", datos, "-o", f"-p {PUERTO} -c listen_addresses=127.0.0.1", "-l", datos / "log.txt", "-w", "start"],
                       check=True, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=90)

        def psql(*archivos, db="postgres"):
            args = [bin_ / f"psql{exe}", "-h", "127.0.0.1", "-p", PUERTO, "-U", "postgres", "-d", db, "-v", "ON_ERROR_STOP=1", "-q", "-X"]
            for a in archivos:
                args += ["-f", str(a)]
            return subprocess.run(args, capture_output=True, text=True, encoding="utf-8", env=env)

        subprocess.run([bin_ / f"createdb{exe}", "-h", "127.0.0.1", "-p", PUERTO, "-U", "postgres", "prueba"], check=True, capture_output=True)
        orden = [AQUI / "stub_supabase.sql", AQUI / "baseline_produccion.sql", RAIZ / "migrations" / "002_estudiantes.sql",
                 Path(os.environ.get("RA_MIGRACION_004", RAIZ / "migrations" / "004_operacion.sql"))]  # la variable permite probar mutaciones
        for f in orden:
            r = psql(f, db="prueba")
            print(("✔ " if r.returncode == 0 else "✘ ") + f.name)
            if r.returncode != 0:
                print(r.stderr.strip()); return 1
        r = psql(AQUI / "rls_test.sql", db="prueba")
        print(r.stdout.strip())
        if r.returncode != 0:
            print("✘ PRUEBAS DE SEGURIDAD FALLARON:\n" + r.stderr.strip()); return 1
        # Idempotencia: volver a aplicar las migraciones no debe fallar ni cambiar el resultado
        for f in orden[2:]:
            r = psql(f, db="prueba")
            print(("✔ re-ejecución idempotente: " if r.returncode == 0 else "✘ re-ejecución falló: ") + f.name)
            if r.returncode != 0:
                print(r.stderr.strip()); return 1
        # Instalación NUEVA: setup_completo.sql sobre una base vacía debe dar el mismo resultado de seguridad
        subprocess.run([bin_ / f"createdb{exe}", "-h", "127.0.0.1", "-p", PUERTO, "-U", "postgres", "limpia"], check=True, capture_output=True)
        for f in [AQUI / "stub_supabase.sql", RAIZ / "setup_completo.sql"]:
            r = psql(f, db="limpia")
            print(("✔ instalación limpia: " if r.returncode == 0 else "✘ instalación limpia falló: ") + f.name)
            if r.returncode != 0:
                print(r.stderr.strip()); return 1
        r = psql(AQUI / "rls_test.sql", db="limpia")
        if r.returncode != 0:
            print("✘ PRUEBAS DE SEGURIDAD FALLARON (instalación limpia):\n" + r.stderr.strip()); return 1
        print("✔ instalación limpia: pruebas de seguridad correctas")
        estado = 0
    finally:
        subprocess.run([bin_ / f"pg_ctl{exe}", "-D", datos, "-m", "immediate", "stop"],
                       stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=60)
        shutil.rmtree(datos, ignore_errors=True)
    return estado


if __name__ == "__main__":
    sys.exit(main())
