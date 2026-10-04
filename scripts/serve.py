#!/usr/bin/env python3
"""Servidor local de desarrollo que replica las cabeceras de seguridad de nginx.conf (incluida la CSP).

  python scripts/serve.py [puerto]      →  http://127.0.0.1:8080

Sirve la raíz del proyecto. Si la app funciona aquí, funciona detrás del nginx del Dockerfile.
"""
import re
import sys
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HEADERS = dict(re.findall(r'add_header ([\w-]+) "([^"]+)" always;', (ROOT / "nginx.conf").read_text(encoding="utf-8")))


class Handler(SimpleHTTPRequestHandler):
    extensions_map = {**SimpleHTTPRequestHandler.extensions_map, ".js": "text/javascript", ".mjs": "text/javascript"}

    def end_headers(self):
        for k, v in HEADERS.items():
            self.send_header(k, v)
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    print(f"Sirviendo {ROOT} en http://127.0.0.1:{port}  (cabeceras: {', '.join(HEADERS)})")
    ThreadingHTTPServer(("127.0.0.1", port), partial(Handler, directory=str(ROOT))).serve_forever()
