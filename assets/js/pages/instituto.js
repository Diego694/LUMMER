/**
 * pages/instituto.js — Mi instituto (nombre, código y seguridad del QR)
 */
import { api }           from '../api.js';
import { DB }            from '../state.js';
import { pintarMarca }   from '../marca.js';
import { guardarPerfil } from '../cola.js';
import { qrModoEfectivo } from '../qr-seguro.js';
import { registerActions, formModal, pageHead, toast, icon } from '../ui.js';
import { esc }           from '../utils.js';

let container;

async function mount(el) {
  container = el;
  container.innerHTML = pageHead('Mi instituto',
    'Nombre y código de registro de tu institución');
  container.innerHTML += '<div id="instituto-content"></div>';

  registerActions({
    'inst-nombre-cambiar': abrirCambiarNombre,
    'inst-qr-guardar': guardarQrModo,
  });

  await cargar();
}

async function cargar() {
  const wrap = document.getElementById('instituto-content');
  if (!wrap) return;
  const nombre = DB.perfil?.colegio || 'Sin nombre';
  let codigo = '—';
  try {
    const c = await api.getCodigoRegistro(DB.cid);
    if (c) codigo = c;
  } catch { /* */ }

  const qrModo = qrModoEfectivo(DB.perfil?.qr_modo);

  wrap.innerHTML = `
    <div class="card">
      <div class="card-body" style="padding:1.5rem">
        <h3 style="margin:0 0 .25rem">${esc(nombre)}</h3>
        <p style="color:var(--text-muted);margin:0 0 1rem">
          Código de registro: <code>${esc(codigo)}</code>
        </p>
        <button class="btn btn-primary" data-action="inst-nombre-cambiar">
          ${icon('edit', 16)} Cambiar nombre
        </button>
        <p style="margin-top:1rem;font-size:.875rem;color:var(--text-muted)">
          Este nombre aparece en el menú, el carnet, el portal del estudiante y los reportes.
        </p>
      </div>
    </div>

    <div class="card" style="margin-top:1.5rem">
      <div class="card-body" style="padding:1.5rem">
        <h3 style="margin:0 0 .5rem">Seguridad del QR de asistencia</h3>
        <p style="color:var(--text-muted);margin:0 0 1.25rem;font-size:.875rem">
          Configura cómo valida la cámara los códigos QR de los carnets de los estudiantes.
        </p>
        <form id="inst-qr-form" onsubmit="return false">
          <div style="display:grid;gap:12px;margin-bottom:1.25rem">
            <label style="display:flex;align-items:flex-start;gap:12px;min-height:44px;padding:8px 12px;border:1.5px solid var(--line);border-radius:var(--radius-sm);cursor:pointer;background:var(--surface)">
              <input type="radio" name="qr_modo" value="off" style="margin-top:4px;min-height:20px;min-width:20px" ${qrModo === 'off' ? 'checked' : ''}>
              <div>
                <strong>Desactivado</strong>
                <div style="font-size:12.5px;color:var(--ink-soft);margin-top:2px">QR fijo</div>
              </div>
            </label>
            <label style="display:flex;align-items:flex-start;gap:12px;min-height:44px;padding:8px 12px;border:1.5px solid var(--line);border-radius:var(--radius-sm);cursor:pointer;background:var(--surface)">
              <input type="radio" name="qr_modo" value="opcional" style="margin-top:4px;min-height:20px;min-width:20px" ${qrModo === 'opcional' ? 'checked' : ''}>
              <div>
                <strong>Opcional</strong>
                <div style="font-size:12.5px;color:var(--ink-soft);margin-top:2px">El estudiante muestra QR que cambia, se aceptan ambos</div>
              </div>
            </label>
            <label style="display:flex;align-items:flex-start;gap:12px;min-height:44px;padding:8px 12px;border:1.5px solid var(--line);border-radius:var(--radius-sm);cursor:pointer;background:var(--surface)">
              <input type="radio" name="qr_modo" value="obligatorio" style="margin-top:4px;min-height:20px;min-width:20px" ${qrModo === 'obligatorio' ? 'checked' : ''}>
              <div>
                <strong>Obligatorio (recomendado)</strong>
                <div style="font-size:12.5px;color:var(--ink-soft);margin-top:2px">La cámara solo acepta el QR que cambia cada 30 s; NFC y código manual siguen valiendo</div>
              </div>
            </label>
          </div>
          <button class="btn btn-primary" data-action="inst-qr-guardar" style="min-height:44px">
            ${icon('check', 16)} Guardar
          </button>
        </form>
      </div>
    </div>
  `;
}

async function guardarQrModo(btn) {
  const cid = DB.cid || DB.perfil?.colegio_id;
  if (!cid) {
    toast('No se encontró tu perfil', 'error');
    return;
  }
  const sel = document.querySelector('input[name="qr_modo"]:checked');
  const modo = sel ? sel.value : 'obligatorio';
  if (btn) btn.disabled = true;
  try {
    await api.cambiarQrModo(cid, modo);
    if (DB.perfil) DB.perfil.qr_modo = modo;
    guardarPerfil({ id: DB.userId, email: DB.userEmail }, DB.perfil);
    toast('Seguridad del QR actualizada', 'success');
  } catch (e) {
    toast('Error: ' + e.message, 'error');
  } finally {
    if (btn) btn.disabled = false;
  }
}

function abrirCambiarNombre() {
  const actual = DB.perfil?.colegio || '';
  formModal({
    title: 'Cambiar nombre de la institución',
    fields: [
      { name: 'nombre', label: 'Nombre', type: 'text',
        value: actual, required: true,
        placeholder: 'Entre 3 y 80 caracteres' }
    ],
    onSubmit: async (data) => {
      const cid = DB.cid || DB.perfil?.colegio_id;
      if (!cid) throw new Error('No se encontró tu perfil');

      await api.renombrarInstituto(cid, data.nombre);

      // Actualizar el perfil en memoria
      if (DB.perfil) DB.perfil.colegio = data.nombre.trim();
      pintarMarca();
      toast('Nombre actualizado', 'success');
      await cargar();
    }
  });
}

export const institutoPage = {
  id: 'instituto',
  title: 'Mi instituto',
  icon: 'briefcase',
  group: 'Gestión',
  soloAdmin: true,
  render: mount,
};

export default institutoPage;
