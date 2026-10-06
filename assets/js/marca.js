// @ts-check
/**
 * marca.js — Nombre de la institución en la interfaz
 *
 * Sin dependencias de DOM pesadas. Usa textContent (nunca innerHTML)
 * para evitar XSS con el nombre.
 */

import { DB } from './state.js';

const LS_KEY = 'ra-ultimo-instituto';

/** Devuelve el nombre de la institución actual */
export function nombreInstituto() {
  if (DB.perfil?.colegio) return DB.perfil.colegio;
  try {
    const v = localStorage.getItem(LS_KEY);
    if (v) return v;
  } catch { /* */ }
  return 'Registro Académico';
}

/** Pinta el nombre en el menú lateral y el título de la pestaña */
export function pintarMarca() {
  const nombre = nombreInstituto();

  // Menú lateral
  const brandStrong = document.querySelector('.side-brand strong');
  if (brandStrong) brandStrong.textContent = nombre;

  const brandSmall = document.querySelector('.side-brand small');
  if (brandSmall) brandSmall.textContent = 'Control de asistencia';

  // Título de la pestaña
  document.title = document.title.includes('·')
    ? document.title.replace(/·.*$/, `· ${nombre}`)
    : nombre;

  // Guardar en localStorage
  try { localStorage.setItem(LS_KEY, nombre); } catch { /* */ }
}

/** En la pantalla de login, muestra la última institución conocida */
export function marcaLogin() {
  let nombre;
  try { nombre = localStorage.getItem(LS_KEY); } catch { /* */ }
  if (!nombre) return;

  const h1 = document.querySelector('#login-form .brand h1');
  if (h1) h1.textContent = nombre;

  const topStrong = document.querySelector('.la-top strong');
  if (topStrong) topStrong.textContent = nombre;
}
