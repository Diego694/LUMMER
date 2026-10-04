// Carnets institucionales con QR: vista previa, descarga PNG/PDF y descarga masiva.
import { CONFIG } from "../config.js";
import { DB, alumnoPorId, opcionesGrado, opcionesNivel } from "../state.js";
import { emptyState, formModal, icon, pageHead, registerActions, toast } from "../ui.js";
import { debounce, downloadFile, esc, etiquetaCiclo, initials, norm } from "../utils.js";

let cq = { q: "", sel: null };

export const carnetPage = {
  id: "carnet", title: "Carnet", icon: "idCard", group: "Gestión",
  render(root, params = {}) {
    if (params.id) cq.sel = alumnoPorId(params.id) || cq.sel;
    root.innerHTML = `
      ${pageHead("Carnet", "Genera el carnet con código QR de cada alumno.", `<button class="btn btn-navy" data-action="carnet-masivo">${icon("download", 16)} Descarga masiva</button>`)}
      <div class="grid-2 split">
        <section class="card flush"><div class="pad"><div class="search"><span class="search-ic">${icon("search", 16)}</span><input class="input" id="carnet-q" placeholder="Buscar alumno…" value="${esc(cq.q)}" aria-label="Buscar alumno"></div></div><div id="carnet-list" class="pick-list"></div></section>
        <section class="card center-col"><div id="carnet-preview"></div><div class="btn-row center" id="carnet-actions" hidden>
          <button class="btn btn-navy" data-action="carnet-png">${icon("download", 16)} Imagen PNG</button><button class="btn btn-teal" data-action="carnet-pdf">${icon("download", 16)} PDF</button></div></section>
      </div>`;
    root.querySelector("#carnet-q").addEventListener("input", debounce((e) => { cq.q = e.target.value; lista(); }, 150));
    lista(); preview();
    function lista() {
      const q = norm(cq.q);
      const l = DB.alumnos.filter((a) => !q || norm(`${a.nombre} ${a.codigo}`).includes(q)).slice(0, 80);
      root.querySelector("#carnet-list").innerHTML = l.length ? l.map((a) => `<button class="pick ${cq.sel?.id === a.id ? "active" : ""}" data-action="carnet-pick" data-id="${a.id}">
        <span class="avatar">${esc(initials(a.nombre))}</span><span><strong>${esc(a.nombre)}</strong><small class="mono">${esc(a.codigo)} · ${esc(a.grado)}</small></span></button>`).join("") : emptyState("Sin alumnos", "Ajusta tu búsqueda.", "search");
    }
    function preview() {
      const holder = root.querySelector("#carnet-preview"), actions = root.querySelector("#carnet-actions");
      if (!cq.sel) { holder.innerHTML = emptyState("Selecciona un alumno", "Aquí verás la vista previa del carnet.", "idCard"); actions.hidden = true; return; }
      const a = cq.sel;
      holder.innerHTML = `<div class="carnet-preview"><div class="c-head">${esc(DB.perfil?.colegio || CONFIG.APP_NAME)} · Carnet institucional</div>
        <div class="c-name">${esc(a.nombre)}</div><div class="c-code">${esc(a.codigo)}</div>
        <div class="c-info"><span>${esc(etiquetaCiclo(a.nivel, a.grado))}</span></div><div class="c-qr" id="carnet-qr"></div></div>`;
      new QRCode(holder.querySelector("#carnet-qr"), { text: a.codigo, width: 112, height: 112, correctLevel: QRCode.CorrectLevel.M });
      actions.hidden = false;
    }
    root._repaint = () => { lista(); preview(); };
  },
};

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath(); ctx.moveTo(x + r, y); ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath();
}

/** Dibuja el carnet en un canvas 560×340 (el QR se genera fuera de pantalla). */
export function carnetCanvas(a, { opaque = false } = {}) {
  return new Promise((resolve) => {
    const canvas = document.createElement("canvas"); canvas.width = 560; canvas.height = 340;
    const ctx = canvas.getContext("2d");
    if (opaque) { ctx.fillStyle = "#fff"; ctx.fillRect(0, 0, 560, 340); } // JPEG no tiene transparencia
    const g = ctx.createLinearGradient(0, 0, 560, 340); g.addColorStop(0, "#16223D"); g.addColorStop(1, "#22335A");
    ctx.fillStyle = g; roundRect(ctx, 0, 0, 560, 340, 24); ctx.fill();
    ctx.fillStyle = "rgba(232,163,61,.16)"; ctx.beginPath(); ctx.arc(540, 20, 110, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = "#E8A33D"; ctx.font = "bold 15px Arial";
    ctx.fillText(`${(DB.perfil?.colegio || CONFIG.APP_NAME).toUpperCase()} · CARNET INSTITUCIONAL`.slice(0, 54), 30, 44);
    ctx.fillStyle = "#FFFFFF"; ctx.font = "bold 26px Arial"; ctx.fillText(a.nombre.slice(0, 32), 30, 100);
    ctx.fillStyle = "#B8C2DC"; ctx.font = "16px monospace"; ctx.fillText(a.codigo, 30, 128);
    ctx.fillStyle = "#CFD7EA"; ctx.font = "14px Arial"; ctx.fillText(etiquetaCiclo(a.nivel, a.grado), 30, 160);
    const tmp = document.createElement("div"); tmp.style.cssText = "position:absolute;left:-9999px";
    document.body.appendChild(tmp);
    new QRCode(tmp, { text: a.codigo, width: 140, height: 140, correctLevel: QRCode.CorrectLevel.M });
    setTimeout(() => {
      ctx.fillStyle = "#fff"; roundRect(ctx, 30, 190, 150, 150, 10); ctx.fill();
      const src = tmp.querySelector("canvas") || tmp.querySelector("img");
      try { if (src) ctx.drawImage(src, 40, 200, 130, 130); } catch { /* QR no disponible */ }
      tmp.remove(); resolve(canvas);
    }, 30);
  });
}

const pdfDoc = () => new window.jspdf.jsPDF({ orientation: "landscape", unit: "pt", format: [360, 220] });

registerActions({
  "carnet-pick": (el) => { cq.sel = alumnoPorId(el.dataset.id); document.getElementById("page-root")._repaint(); },
  "carnet-png": async () => {
    if (!cq.sel) return;
    const c = await carnetCanvas(cq.sel);
    await downloadFile(`carnet-${cq.sel.codigo}.png`, await new Promise((r) => c.toBlob(r, "image/png")));
  },
  "carnet-pdf": async () => {
    if (!cq.sel) return;
    const pdf = pdfDoc(); pdf.addImage((await carnetCanvas(cq.sel, { opaque: true })).toDataURL("image/jpeg", 0.92), "JPEG", 0, 0, 360, 220);
    await downloadFile(`carnet-${cq.sel.codigo}.pdf`, pdf.output("blob"));
  },
  "carnet-masivo": () => {
    const cuenta = (n, g) => DB.alumnos.filter((a) => (!n || a.nivel === n) && (!g || a.grado === g));
    const m = formModal({
      title: "Descarga masiva de carnets", submitLabel: "Generar PDF",
      fields: [
        { name: "nivel", label: "Carrera", type: "select", half: true, options: opcionesNivel(true), value: "",
          onChange: (v, ctl) => { ctl.setOptions("grado", opcionesGrado(v, true), ""); actualiza(); } },
        { name: "grado", label: "Ciclo", type: "select", half: true, options: opcionesGrado("", true), value: "", onChange: () => actualiza() },
      ],
      onSubmit: async ({ nivel, grado }) => {
        const l = cuenta(nivel, grado);
        if (!l.length) throw new Error("No hay alumnos para este filtro.");
        toast(`Generando ${l.length} carnets…`);
        const pdf = pdfDoc();
        for (let i = 0; i < l.length; i++) {
          if (i) pdf.addPage([360, 220], "landscape");
          pdf.addImage((await carnetCanvas(l[i], { opaque: true })).toDataURL("image/jpeg", 0.92), "JPEG", 0, 0, 360, 220);
        }
        await downloadFile(`carnets-${nivel || "todos"}-${grado || "todos"}.pdf`, pdf.output("blob"));
        toast("Descarga completada", "success");
      },
    });
    const nota = document.createElement("p"); nota.className = "muted"; m.el.querySelector(".form-grid").after(nota);
    function actualiza() { const f = m.el.querySelector("#modal-form").elements; nota.textContent = `${cuenta(f.nivel.value, f.grado.value).length} alumno(s) coinciden con este filtro.`; }
    actualiza();
  },
});
