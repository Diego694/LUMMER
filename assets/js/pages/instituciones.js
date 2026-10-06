// @ts-check
/**
 * pages/instituciones.js — Gestión multi-institución (superadmin)
 */
import { api }           from '../api.js';
import { DB }            from '../state.js';
import { esSuper }       from '../permisos.js';
import { pintarMarca }   from '../marca.js';
import { registerActions, formModal, confirmDialog,
         emptyState, badge, toast,
         icon }          from '../ui.js';
import { esc }           from '../utils.js';

let container;

function resolveId(/** @type {any} */ target) {
  if (typeof target === 'string') return target;
  return target?.dataset?.id || '';
}

async function mount(/** @type {any} */ el) {
  container = el;
  container.innerHTML = `
    <div id="inst-hero-container"></div>
    <div id="inst-content"></div>
  `;

  registerActions({
    'inst-crear':     abrirCrear,
    'inst-renombrar': el => abrirRenombrar(el),
    'inst-entrar':    el => confirmarEntrar(el),
    'inst-admin':     el => abrirAsignarAdmin(el),
    'inst-activar':   el => toggleActivar(el),
    'inst-copiar':    el => copiarCodigo(el),
  });

  await cargar();
}

function renderHero(/** @type {any} */ stats) {
  const heroWrap = /** @type {HTMLElement} */ (document.getElementById('inst-hero-container'));
  if (!heroWrap) return;

  const instVal = stats !== null ? stats.totalInst.toLocaleString('es-PE') : '<span class="skeleton inst-stat-skel"></span>';
  const alumVal = stats !== null ? stats.totalAlumnos.toLocaleString('es-PE') : '<span class="skeleton inst-stat-skel"></span>';
  const persVal = stats !== null ? stats.totalPersonal.toLocaleString('es-PE') : '<span class="skeleton inst-stat-skel"></span>';

  heroWrap.innerHTML = `
    <header class="inst-hero" aria-labelledby="inst-hero-title">
      <div class="inst-hero-shapes" aria-hidden="true">
        <i class="inst-orb inst-orb-amber"></i>
        <i class="inst-orb inst-orb-blue"></i>
        <i class="inst-shape inst-ring"></i>
        <i class="inst-shape inst-dot"></i>
      </div>
      <div class="inst-hero-inner">
        <div class="inst-hero-header">
          <div class="inst-hero-text">
            <h1 id="inst-hero-title" class="inst-hero-title">Instituciones</h1>
            <p class="inst-hero-sub">Crear y administrar instituciones del sistema</p>
          </div>
          <div class="inst-hero-actions">
            <button class="btn btn-primary" data-action="inst-crear">
              ${icon('plus', 16)} Crear institución
            </button>
          </div>
        </div>
        <div class="inst-stats" role="region" aria-label="Métricas de instituciones">
          <div class="inst-stat-card">
            <span class="inst-stat-val">${instVal}</span>
            <span class="inst-stat-lbl">Institutos</span>
          </div>
          <div class="inst-stat-card">
            <span class="inst-stat-val">${alumVal}</span>
            <span class="inst-stat-lbl">Alumnos totales</span>
          </div>
          <div class="inst-stat-card">
            <span class="inst-stat-val">${persVal}</span>
            <span class="inst-stat-lbl">Personal total</span>
          </div>
        </div>
      </div>
    </header>
  `;
}

function renderSkeletonGrid(count = 6) {
  return `
    <div class="inst-grid" aria-busy="true" aria-label="Cargando instituciones">
      ${Array.from({ length: count }, () => `
        <div class="card inst-card inst-card-skeleton">
          <div class="inst-card-top">
            <div class="skeleton" style="width: 70px; height: 20px; border-radius: 99px; margin-bottom: 12px;"></div>
            <div class="skeleton" style="width: 85%; height: 22px; margin-bottom: 8px;"></div>
            <div class="skeleton" style="width: 55%; height: 16px; margin-bottom: 16px;"></div>
            <div class="skeleton" style="width: 130px; height: 28px; border-radius: var(--radius-sm); margin-bottom: 16px;"></div>
          </div>
          <div class="inst-card-mid">
            <div class="skeleton" style="width: 90%; height: 16px; margin-bottom: 8px;"></div>
            <div class="skeleton" style="width: 60%; height: 14px;"></div>
          </div>
          <div class="inst-card-actions">
            <div class="skeleton" style="flex: 1 1 70px; height: 32px; border-radius: var(--radius-sm);"></div>
            <div class="skeleton" style="flex: 1 1 70px; height: 32px; border-radius: var(--radius-sm);"></div>
            <div class="skeleton" style="flex: 1 1 70px; height: 32px; border-radius: var(--radius-sm);"></div>
          </div>
        </div>
      `).join('')}
    </div>
  `;
}

function renderGrid(/** @type {any} */ items) {
  return `
    <div class="inst-grid" role="list" aria-label="Lista de instituciones">
      ${items.map((/** @type {any} */ inst) => {
        const esActual = !!inst.actual;
        const estaActiva = !!inst.activo;
        const fecha = inst.creado_en
          ? new Date(inst.creado_en).toLocaleDateString('es-PE')
          : '—';
        const codigo = inst.codigo_registro || '';
        const borderClass = esActual
          ? 'inst-card-actual'
          : (estaActiva ? 'inst-card-activa' : 'inst-card-inactiva');

        return `
          <article class="card inst-card ${borderClass}" role="listitem">
            <div class="inst-card-top">
              <div class="inst-card-badges">
                ${esActual ? badge('Actual', 'navy') : ''}
                ${estaActiva ? badge('Activa', 'green') : badge('Inactiva', 'amber')}
              </div>
              <h2 class="inst-card-name" title="${esc(inst.nombre)}">${esc(inst.nombre)}</h2>

              <div class="inst-code-box">
                <span class="inst-code-lbl">Código:</span>
                <code class="inst-code-chip mono">${esc(codigo || '—')}</code>
                ${codigo ? `
                  <button type="button" class="inst-copy-btn" data-action="inst-copiar" data-codigo="${esc(codigo)}" aria-label="Copiar código de registro" title="Copiar código">
                    <svg class="icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                      <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                    </svg>
                  </button>
                ` : ''}
              </div>
            </div>

            <div class="inst-card-mid">
              <div class="inst-metrics">
                <span class="inst-metric" title="Alumnos registrados">
                  ${icon('users', 15)}
                  <strong>${inst.alumnos ?? 0}</strong> alumnos
                </span>
                <span class="inst-metric" title="Personal registrado">
                  ${icon('briefcase', 15)}
                  <strong>${inst.personal ?? 0}</strong> personal
                </span>
              </div>
              <div class="inst-card-date">
                ${icon('calendar', 14)} Creada el ${esc(fecha)}
              </div>
            </div>

            <div class="inst-card-actions">
              ${!esActual ? `
                <button class="btn btn-primary btn-sm inst-btn-entrar" data-action="inst-entrar" data-id="${inst.id}" title="Entrar a esta institución">
                  Entrar
                </button>
              ` : ''}
              <button class="btn btn-outline btn-sm" data-action="inst-renombrar" data-id="${inst.id}" title="Renombrar institución">
                ${icon('edit', 14)} Renombrar
              </button>
              <button class="btn btn-outline btn-sm" data-action="inst-admin" data-id="${inst.id}" title="Asignar administrador">
                ${icon('userCheck', 14)} Admin
              </button>
              <button class="btn btn-sm ${estaActiva ? 'btn-ghost-danger' : 'btn-outline'}" data-action="inst-activar" data-id="${inst.id}" title="${estaActiva ? 'Desactivar institución' : 'Activar institución'}">
                ${icon(estaActiva ? 'x' : 'check', 14)} ${estaActiva ? 'Desactivar' : 'Activar'}
              </button>
            </div>
          </article>
        `;
      }).join('')}
    </div>
  `;
}

async function cargar() {
  const wrap = /** @type {HTMLElement} */ (document.getElementById('inst-content'));
  if (!wrap) return;

  renderHero(null);
  wrap.innerHTML = renderSkeletonGrid(6);

  try {
    const lista = await api.saListar();
    const items = typeof lista === 'string' ? JSON.parse(lista) : lista;

    if (!items || !items.length) {
      renderHero({ totalInst: 0, totalAlumnos: 0, totalPersonal: 0 });
      wrap.innerHTML = emptyState('No hay instituciones registradas', 'Crea la primera institución con «Crear institución».', 'layers') +
        `<div style="text-align:center;margin-top:1.5rem">
          <button class="btn btn-primary" data-action="inst-crear">
            ${icon('plus', 16)} Crear institución
          </button>
        </div>`;
      return;
    }

    const totalInst = items.length;
    const totalAlumnos = items.reduce((/** @type {any} */ acc, /** @type {any} */ cur) => acc + (Number(cur.alumnos) || 0), 0);
    const totalPersonal = items.reduce((/** @type {any} */ acc, /** @type {any} */ cur) => acc + (Number(cur.personal) || 0), 0);

    renderHero({ totalInst, totalAlumnos, totalPersonal });
    wrap.innerHTML = renderGrid(items);
  } catch (/** @type {any} */ e) {
    renderHero({ totalInst: 0, totalAlumnos: 0, totalPersonal: 0 });
    const msg = e.message || '';
    if (msg.includes('sa_listar') || msg.includes('011') || msg.includes('does not exist')) {
      wrap.innerHTML = `<div class="card"><p class="err-msg" role="alert">Falta aplicar la migración 011 en la base de datos para habilitar multi-institución.</p></div>`;
    } else {
      wrap.innerHTML = `<div class="card"><p class="err-msg" role="alert">${esc(msg)}</p></div>`;
    }
  }
}

async function copiarCodigo(/** @type {any} */ target) {
  const codigo = typeof target === 'string' ? target : target?.dataset?.codigo;
  if (!codigo || codigo === '—') return;
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(codigo);
    } else {
      const ta = document.createElement('textarea');
      ta.value = codigo;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.focus();
      ta.select();
      document.execCommand('copy');
      ta.remove();
    }
    toast('Código copiado', 'success');
  } catch {
    toast('No se pudo copiar el código', 'error');
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

async function abrirRenombrar(/** @type {any} */ target) {
  const id = resolveId(target);
  let nombreActual = '';
  try {
    const lista = await api.saListar();
    const items = typeof lista === 'string' ? JSON.parse(lista) : lista;
    nombreActual = items.find((/** @type {any} */ i) => i.id === id)?.nombre || '';
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

async function confirmarEntrar(/** @type {any} */ target) {
  const id = resolveId(target);
  if (!(await confirmDialog({
    title: 'Entrar a esta institución',
    message: 'Pasarás a administrar esta institución. Tu sesión se recargará y verás los datos de la nueva institución.',
    confirmLabel: 'Entrar',
    danger: false,
  }))) return;

  try {
    await api.saEntrar(id);
    location.reload();
  } catch (/** @type {any} */ e) {
    toast(e.message || 'No se pudo entrar a la institución', 'error');
  }
}

function abrirAsignarAdmin(/** @type {any} */ target) {
  const id = resolveId(target);
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

async function toggleActivar(/** @type {any} */ target) {
  const id = resolveId(target);
  try {
    const lista = await api.saListar();
    const items = typeof lista === 'string' ? JSON.parse(lista) : lista;
    const inst = items.find((/** @type {any} */ i) => i.id === id);
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
  } catch (/** @type {any} */ e) {
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
