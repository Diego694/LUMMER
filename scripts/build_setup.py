#!/usr/bin/env python3
"""Genera supabase/setup_completo.sql = schema.sql + migraciones 002 y 004 (instalación NUEVA en un solo archivo).
La 003 (datos del instituto original) no se incluye: es una migración de datos puntual."""
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent / "supabase"
PARTES = [RAIZ / "schema.sql", RAIZ / "migrations" / "002_estudiantes.sql", RAIZ / "migrations" / "004_operacion.sql"]
cab = "-- Instalación completa (GENERADO por scripts/build_setup.py; no editar a mano).\n-- Ejecutar en Supabase → SQL Editor sobre un proyecto NUEVO. Luego, el bloque «Alta de un instituto» de schema.sql.\n\n"
cuerpo = "".join(f"\n-- ======================== {p.name} ========================\n" + p.read_text(encoding="utf-8") + "\n" for p in PARTES)
(RAIZ / "setup_completo.sql").write_text(cab + cuerpo, encoding="utf-8", newline="\n")
print("OK supabase/setup_completo.sql")
