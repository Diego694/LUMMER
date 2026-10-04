// Pasar los datos del modo LOCAL (programa de PC) a la base ONLINE. Solo en el .exe, en modo online y como administrador.
// Nunca pisa lo que ya existe online: solo agrega lo que falta. Los datos locales se conservan en el equipo.
import { api } from "../api.js";
import { esEscritorio, modoActual } from "../config.js";
import { asistenciasParaOnline, planFusion } from "../fusion.js";
import { DB, loadAll } from "../state.js";
import { emptyState, icon, pageHead, registerActions, toast } from "../ui.js";
import { esc } from "../utils.js";

const leerLocal = () => { try { return JSON.parse(localStorage.getItem("ra-local-db-v1")); } catch { return null; } };
let plan = null;

function pintar(el) {
  const local = leerLocal();
  const box = el.querySelector("#mg-box");
  if (!esEscritorio() || modoActual() !== "online") { box.innerHTML = emptyState("Disponible en el programa de PC", "Abre el programa de PC en modo online para pasar los datos que registraste en modo local.", "download"); return; }
  if (!local || !(local.alumnos?.length || local.asistencias?.length)) { box.innerHTML = emptyState("No hay datos locales", "Cuando trabajes en modo local, aquí podrás enviarlos a la base online.", "info"); return; }
  plan = planFusion(local, { niveles: DB.nivelesRaw, grados: DB.grados, alumnos: DB.alumnos, comunicados: DB.comunicados, cursos: DB.cursos });
  const r = plan.resumen;
  const fila = (t, n, extra = "") => `<tr><td>${t}</td><td><strong>${n}</strong> ${extra}</td></tr>`;
  box.innerHTML = `<div class="table-wrap"><table><tbody>
    ${fila("Carreras nuevas", r.niveles)}${fila("Ciclos nuevos", r.grados)}${fila("Alumnos nuevos", r.alumnos, r.alumnosExistentes ? `<small class="muted">(${r.alumnosExistentes} ya existen online por su código y no se tocan)</small>` : "")}
    ${fila("Asistencias", r.asistencias, '<small class="muted">(las que ya estén online se ignoran)</small>')}${fila("Comunicados nuevos", r.comunicados)}${fila("Cursos nuevos", r.cursos)}</tbody></table></div>
    <div class="btn-row" style="margin-top:14px"><button class="btn btn-primary" data-action="mg-enviar">${icon("upload", 16)} Enviar a la base online</button></div>
    <p class="err-msg" id="mg-err" hidden></p>`;
}

export const migrarPage = {
  id: "migrar", title: "Datos del modo local", icon: "upload", group: "Sistema", soloAdmin: true,
  render(el) {
    el.innerHTML = `${pageHead("Datos del modo local", "Envía a la base online lo que registraste sin internet. No borra nada: tus datos locales siguen en este equipo.")}<section class="card" id="mg-box"></section>`;
    pintar(el);
  },
};

registerActions({
  "mg-enviar": async (btnEl) => {
    if (!plan) return;
    const err = document.getElementById("mg-err");
    btnEl.disabled = true; btnEl.textContent = "Enviando…"; err.hidden = true;
    try {
      const local = leerLocal();
      const cid = DB.cid;
      for (const n of plan.niveles) await api.save("niveles", { colegio_id: cid, nombre: n.nombre });
      for (const g of plan.grados) await api.save("grados", { colegio_id: cid, nivel: g.nivel, nombre: g.nombre });
      const alum = plan.alumnos.map(({ id, colegio_id, qr_secreto, codigo_apoderado, ...a }) => ({ ...a, colegio_id: cid }));
      if (alum.length) await api.upsertAlumnos(alum);
      for (const c of plan.comunicados) await api.save("comunicados", { colegio_id: cid, titulo: c.titulo, mensaje: c.mensaje, fecha: c.fecha });
      for (const c of plan.cursos) await api.save("cursos", { colegio_id: cid, nivel: c.nivel, grado: c.grado || null, nombre: c.nombre, docente: c.docente || "", activo: c.activo !== false });
      await loadAll();   // ahora los alumnos online tienen id: se enlazan por código
      const { filas, sinAlumno } = asistenciasParaOnline(plan.asistencias, local.alumnos || [], DB.alumnos, cid, DB.userId);
      let n = 0;
      for (let i = 0; i < filas.length; i += 200) n += await api.registrarMasivo(filas.slice(i, i + 200));
      await loadAll();
      toast(`Listo: ${plan.alumnos.length} alumnos y ${n} asistencias enviados${sinAlumno ? ` (${sinAlumno} sin alumno online)` : ""}.`, "success");
      pintar(document.getElementById("page-root"));
    } catch (e) { err.textContent = "No se pudo completar: " + e.message + " Puedes reintentar: lo ya enviado no se duplica."; err.hidden = false; btnEl.disabled = false; btnEl.textContent = "Enviar a la base online"; }
  },
});
void esc;
