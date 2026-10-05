/**
 * pages/instituto.js — Mi instituto (nombre y código)
 */
import { api }           from '../api.js';
import { DB }            from '../state.js';
import { pintarMarca }   from '../marca.js';
import { registerActions, formModal, pageHead, toast, icon } from '../ui.js';
import { esc }           from '../utils.js';

let container;

async function mount(el) {
  container = el;
  container.innerHTML = pageHead('Mi instituto',
    'Nombre y código de registro de tu institución');
  container.innerHTML += '<div id="instituto-content"></div>';

  registerActions({
    'inst-nombre-cambiar': abrirCambiarNombre
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
  `;
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
