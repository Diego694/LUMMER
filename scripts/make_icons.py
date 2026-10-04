#!/usr/bin/env python3
"""Genera los iconos PNG de la PWA (sin dependencias; rasteriza con supermuestreo).

  python scripts/make_icons.py

Logo: fondo azul marino con un check ámbar. El icono "maskable" es de sangrado completo con el
check dentro de la zona segura (80 %).
"""
import struct
import zlib
from pathlib import Path

NAVY, AMBER = (22, 34, 61), (232, 163, 61)
OUT = Path(__file__).resolve().parent.parent / "assets" / "icons"


def dist_seg(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return ((px - ax - t * dx) ** 2 + (py - ay - t * dy) ** 2) ** 0.5


def render(size, rounded, scale):
    ss = 3
    rows = []
    r = size * 0.22
    # check en coordenadas normalizadas (0..1), reducido por `scale` alrededor del centro
    pts = [(0.28, 0.53), (0.43, 0.68), (0.73, 0.35)]
    pts = [(0.5 + (x - 0.5) * scale, 0.5 + (y - 0.5) * scale) for x, y in pts]
    w = 0.085 * scale
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            acc = [0, 0, 0, 0]
            for sy in range(ss):
                for sx in range(ss):
                    fx, fy = (x + (sx + 0.5) / ss), (y + (sy + 0.5) / ss)
                    inside = True
                    if rounded:
                        cx, cy = min(max(fx, r), size - r), min(max(fy, r), size - r)
                        inside = (fx - cx) ** 2 + (fy - cy) ** 2 <= r * r
                    if not inside:
                        continue
                    nx, ny = fx / size, fy / size
                    d = min(dist_seg(nx, ny, *pts[0], *pts[1]), dist_seg(nx, ny, *pts[1], *pts[2]))
                    c = AMBER if d <= w else NAVY
                    acc[0] += c[0]; acc[1] += c[1]; acc[2] += c[2]; acc[3] += 255
            n = ss * ss
            a = acc[3] // n
            cov = max(acc[3] // 255, 1)
            row += bytes([acc[0] // cov, acc[1] // cov, acc[2] // cov, a]) if acc[3] else bytes([0, 0, 0, 0])
        rows.append(bytes(row))
    return rows


def write_png(path, size, rows):
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b"")
    path.write_bytes(png)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, size, rounded, scale in [("icon-192.png", 192, True, 1.0), ("icon-512.png", 512, True, 1.0), ("icon-maskable-512.png", 512, False, 0.8)]:
        write_png(OUT / name, size, render(size, rounded, scale))
        print("✔", name)
