// Copia la web a desktop/app y descarga las librerías de CDN a app/vendor para que el .exe funcione 100 % sin internet.
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const aqui = dirname(fileURLToPath(import.meta.url));
const raiz = join(aqui, "..", "..");
const app = join(aqui, "..", "app");
rmSync(app, { recursive: true, force: true });
mkdirSync(join(app, "vendor"), { recursive: true });
for (const f of ["index.html", "privacidad.html", "manifest.webmanifest"]) cpSync(join(raiz, f), join(app, f));
cpSync(join(raiz, "assets"), join(app, "assets"), { recursive: true });

const html = readFileSync(join(raiz, "index.html"), "utf8");
const urls = [...new Set([...html.matchAll(/<script[^>]+src="(https:\/\/[^"]+)"/g)].map((m) => m[1]))];
const mapa = {};
let i = 0;
for (const url of urls) {
  const nombre = `${String(++i).padStart(2, "0")}-${url.split("/").pop().split("?")[0] || "lib.js"}`;
  const r = await fetch(url, { redirect: "follow" });
  if (!r.ok) throw new Error(`No se pudo descargar ${url}: ${r.status}`);
  const buf = Buffer.from(await r.arrayBuffer());
  if (buf.length < 1000) throw new Error(`Descarga sospechosamente pequeña: ${url}`);
  writeFileSync(join(app, "vendor", nombre), buf);
  mapa[url] = `vendor/${nombre}`;
  console.log("vendor", nombre, buf.length);
}
writeFileSync(join(app, "vendor", "map.json"), JSON.stringify(mapa, null, 1));
console.log(`OK: ${urls.length} librerías incluidas`);
