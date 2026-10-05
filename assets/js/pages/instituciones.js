/**
 * pages/instituciones.js — Gestión multi-institución (superadmin)
 */
import { api }           from '../api.js';
import { DB }            from '../state.js';
import { esSuper }       from '../permisos.js';
import { pintarMarca }   from '../marca.js';
import { registerActions, formModal, confirmDialog,
         pageHead, emptyState, badge, toast,
         icon, skeleton }     from '../ui.js';
import { esc }           from '../utils.js';

let container;

async function mount(el) {
  container = el;
  container.innerHTML = pageHead('Instituciones',
    'Crear y administrar instituciones del sistema',
    `<button class="btn btn-primary" data-action="inst-crear">${icon('plus', 16)} Crear institución</button>`);
  container.innerHTML += '<div id="inst-content"></div>';

  registerActions({
    'inst-crear':     abrirCrear,
    'inst-renombrar': id => abrirRenombrar(id),
    'inst-entrar':    id => confirmarEntrar(id),
    'inst-admin':     id => abrirAsignarAdmin(id),
    'inst-activar':   id => toggleActivar(id)
  });

  await cargar();
}

async function cargar() {
  const wrap = document.getElementById('inst-content');
  if (!wrap) return;
  wrap.innerHTML = `<div class="card flush">${skeleton(5)}</div>`;

  try {
    const lista = await api.saListar();
    const items = typeof lista === 'string' ? JSON.parse(lista) : lista;

    if (!items || !items.length) {
      wrap.innerHTML = emptyState('No hay instituciones registradas', 'Crea la primera institución con «Crear institución».', 'layers') +
        `<div style="text-align:center;margin-top:1rem">
          <button class="btn btn-primary" data-action="inst-crear">
            ${icon('plus', 16)} Crear institución
          </button>
        </div>`;
      return;
    }

    wrap.innerHTML = `
      <div class="card flush">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Código de registro</th>
                <th>Alumnos</th>
                <th>Personal</th>
                <th>Creada</th>
                <th>Estado</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              ${items.map(inst => {
                const esActual = inst.actual;
                const fecha = inst.creado_en
                  ? new Date(inst.creado_en).toLocaleDateString('es-PE')
                  : '—';
                return `
                  <tr>
                    <td>
                      <strong>${esc(inst.nombre)}</strong>
                      ${esActual ? ` ${badge('Actual', 'navy')}` : ''}
                    </td>
                    <td><code>${esc(inst.codigo_registro || '—')}</code></td>
                    <td>${inst.alumnos ?? 0}</td>
                    <td>${inst.personal ?? 0}</td>
                    <td>${esc(fecha)}</td>
                    <td>${inst.activo
                      ? badge('Activa', 'green')
                      : badge('Inactiva', 'amber')}</td>
                    <td class="t-right nowrap">
                      <button class="btn btn-outline btn-sm" data-action="inst-renombrar" data-id="${inst.id}" title="Renombrar">${icon('edit', 14)} Renombrar</button>
                      ${!esActual ? `<button class="btn btn-outline btn-sm" data-action="inst-entrar" data-id="${inst.id}" title="Entrar a esta institución">Entrar</button>` : ''}
                      <button class="btn btn-outline btn-sm" data-action="inst-admin" data-id="${inst.id}" title="Asignar administrador">${icon('userCheck', 14)} Admin</button>
                      <button class="btn btn-sm ${inst.activo ? 'btn-ghost-danger' : 'btn-outline'}" data-action="inst-activar" data-id="${inst.id}" title="${inst.activo ? 'Desactivar' : 'Activar'}">${icon(inst.activo ? 'x' : 'check', 14)} ${inst.activo ? 'Desactivar' : 'Activar'}</button>
                    </td>
                  </tr>
                `;
              }).join('')}
            </tbody>
          </table>
        </div>
      </div>
    `;
  } catch (e) {
    const msg = e.message || '';
    if (msg.includes('sa_listar') || msg.includes('011') || msg.includes('does not exist')) {
      wrap.innerHTML = `<div class="card"><p class="err-msg" role="alert">Falta aplicar la migración 011 en la base de datos para habilitar multi-institución.</p></div>`;
    } else {
      wrap.innerHTML = `<div class="card"><p class="err-msg" role="alert">${esc(msg)}</p></div>`;
    }
  }
}

function abrirCrear() {
  formModal({
    title: 'Crear institución',
    fields: [
      { name: 'nombre', label: 'Nombre de la institución', type: 'text',
        required: true, placeholder: 'Entre 3 y 80 caracteres' },
      { name: 'codigo', label: 'Código de registro (opcional)', type: 'text',
        placeholder: 'Vacío para automático (6–20 caracteres A-Z 0-9)' }
    ],
    onSubmit: async (data) => {
      await api.saCrear(data.nombre, data.codigo || null);
      toast('Institución creada', 'success');
      await cargar();
    }
  });
}

async function abrirRenombrar(id) {
  let nombreActual = '';
  try {
    const lista = await api.saListar();
    const items = typeof lista === 'string' ? JSON.parse(lista) : lista;
    nombreActual = items.find(i => i.id === id)?.nombre || '';
  } catch { /* */ }

  formModal({
    title: 'Renombrar institución',
    fields: [
      { name: 'nombre', label: 'Nuevo nombre', type: 'text',
        value: nombreActual, required: true, placeholder: 'Entre 3 y 80 caracteres' }
    ],
    onSubmit: async (data) => {
      await api.saRenombrar(id, data.nombre);
      if (DB.cid === id && DB.perfil) DB.perfil.colegio = data.nombre.trim();
      pintarMarca();
      toast('Institución renombrada', 'success');
      await cargar();
    }
  });
}

async function confirmarEntrar(id) {
  if (!(await confirmDialog({
    title: 'Entrar a esta institución',
    message: 'Pasarás a administrar esta institución. Tu sesión se recargará y verás los datos de la nueva institución.',
    confirmLabel: 'Entrar',
    danger: false,
  }))) return;

  try {
    await api.saEntrar(id);
    location.reload();
  } catch (e) {
    toast(e.message || 'No se pudo entrar a la institución', 'error');
  }
}

function abrirAsignarAdmin(id) {
  formModal({
    title: 'Asignar administrador',
    fields: [
      { name: 'email', label: 'Correo electrónico', type: 'text',
        required: true,
        placeholder: 'admin@instituto.pe' }
    ],
    onSubmit: async (data) => {
      await api.saAsignarAdmin(id, data.email);
      toast('Administrador asignado', 'success');
      await cargar();
    }
  });
}

async function toggleActivar(id) {
  try {
    const lista = await api.saListar();
    const items = typeof lista === 'string' ? JSON.parse(lista) : lista;
    const inst = items.find(i => i.id === id);
    if (!inst) return;

    const nuevoEstado = !inst.activo;
    const accion = nuevoEstado ? 'activar' : 'desactivar';

    if (!(await confirmDialog({
      title: `${nuevoEstado ? 'Activar' : 'Desactivar'} institución`,
      message: `¿Deseas ${accion} «${esc(inst.nombre)}»?`,
      confirmLabel: nuevoEstado ? 'Activar' : 'Desactivar',
      danger: !nuevoEstado,
    }))) return;

    await api.saActivar(id, nuevoEstado);
    toast(`Institución ${nuevoEstado ? 'activada' : 'desactivada'}`, 'success');
    await cargar();
  } catch (e) {
    toast(e.message || 'Error al cambiar estado', 'error');
  }
}

export const institucionesPage = {
  id: 'instituciones',
  title: 'Instituciones',
  icon: 'layers',
  group: 'Sistema',
  soloSuper: true,
  render: mount,
};

export default institucionesPage;
