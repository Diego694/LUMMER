#!/usr/bin/env python3
"""Verificaciones estáticas del proyecto (sin dependencias). Usado localmente y en CI.

  python scripts/check.py

Comprueba: que cada import relativo de los módulos JS exista, que index.html referencie
archivos existentes, que no haya secretos reales en config.js y que el SQL tenga RLS en todas
las tablas. Sale con código 1 si algo falla.
"""
import base64
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
errors: list[str] = []


def check_js_imports() -> int:
    n = 0
    for js in (ROOT / "assets" / "js").rglob("*.js"):
        for m in re.finditer(r'(?:import|export)[^"\']*?from\s+["\'](\.[^"\']+)["\']', js.read_text(encoding="utf-8")):
            n += 1
            if not (js.parent / m.group(1)).resolve().exists():
                errors.append(f"{js.relative_to(ROOT)}: import inexistente {m.group(1)}")
    return n


def check_index_refs() -> int:
    html = (ROOT / "index.html").read_text(encoding="utf-8")
    refs = re.findall(r'(?:src|href)="((?!https?:|data:|#)[^"]+)"', html)
    for r in refs:
        if not (ROOT / r).exists():
            errors.append(f"index.html referencia un archivo inexistente: {r}")
    return len(refs)


def check_pwa() -> int:
    """El manifiesto, sus iconos y todo el 'shell' que precachea sw.js deben existir (si no, el SW falla al instalar)."""
    sw = (ROOT / "sw.js").read_text(encoding="utf-8")
    shell = re.search(r"const SHELL = \[(.*?)\];", sw, re.S)
    files = re.findall(r'"([^"]+)"', shell.group(1)) if shell else []
    if not files:
        errors.append("sw.js: no se pudo leer la lista SHELL")
    manifest = json.loads((ROOT / "manifest.webmanifest").read_text(encoding="utf-8"))
    files += [i["src"] for i in manifest.get("icons", [])]
    for f in files:
        if f != "./" and not (ROOT / f).exists():
            errors.append(f"PWA: archivo inexistente {f}")
    # todos los módulos JS deberían estar precacheados para que la app abra sin conexión
    for js in (ROOT / "assets" / "js").rglob("*.js"):
        if js.relative_to(ROOT).as_posix() not in files:
            errors.append(f"sw.js: {js.relative_to(ROOT).as_posix()} no está en SHELL (la app no abriría sin conexión)")
    return len(files)


def check_no_secrets() -> None:
    cfg = (ROOT / "assets" / "js" / "config.js").read_text(encoding="utf-8")
    url = re.search(r'SUPABASE_URL:\s*"([^"]*)"', cfg)
    key = re.search(r'SUPABASE_ANON_KEY:\s*"([^"]*)"', cfg)
    if not url or not key:
        errors.append("config.js: no se encontraron SUPABASE_URL / SUPABASE_ANON_KEY.")
        return
    if "TU-PROYECTO" in url.group(1) or "TU-ANON-KEY" in key.group(1):
        print("ℹ config.js con placeholders → la app corre en modo demo")
    # La anon key (JWT con role=anon) es pública por diseño; una service_role NUNCA debe llegar al cliente.
    for tok in re.findall(r"eyJ[\w-]+\.([\w-]+)\.[\w-]+", cfg):
        try:
            role = json.loads(base64.urlsafe_b64decode(tok + "=" * (-len(tok) % 4))).get("role")
        except Exception:
            continue
        if role == "service_role":
            errors.append("config.js contiene una clave service_role: ¡revócala en Supabase y quítala del repositorio!")
    if re.search(r"sb_secret_[\w-]+", cfg):
        errors.append("config.js contiene una secret key de Supabase (sb_secret_…).")


def check_sql_rls() -> int:
    sql = (ROOT / "supabase" / "schema.sql").read_text(encoding="utf-8")
    tables = re.findall(r"create table if not exists public\.(\w+)", sql)
    for t in tables:
        if not re.search(rf"alter table public\.{t}\s+enable row level security", sql):
            errors.append(f"schema.sql: la tabla {t} no tiene RLS habilitado")
    return len(tables)


if __name__ == "__main__":
    imports, refs, tables, pwa = check_js_imports(), check_index_refs(), check_sql_rls(), check_pwa()
    check_no_secrets()
    print(f"imports JS: {imports} · referencias index.html: {refs} · tablas con RLS: {tables} · archivos PWA: {pwa}")
    if errors:
        print("\n".join("✘ " + e for e in errors))
        sys.exit(1)
    print("✔ todo correcto")
