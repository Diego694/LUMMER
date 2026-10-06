// @ts-check
// Comprobación de compatibilidad (script clásico, se ejecuta antes que los módulos). En teléfonos con un WebView/navegador
// muy antiguo la app no puede arrancar; en vez de una pantalla en blanco, se explica qué hacer.
(function () {
  /** @type {string[]} */
  var faltan = [];
  try {
    if (!("noModule" in HTMLScriptElement.prototype)) faltan.push("módulos ES");
    // Sin eval (la CSP lo bloquea): se comprueban funciones que llegaron junto con la sintaxis moderna (Chrome 80+).
    if (!Promise.allSettled || !Object.fromEntries || !window.fetch || !window.IntersectionObserver || !String.prototype.matchAll || !Array.prototype.at) faltan.push("funciones modernas");
  } catch (e) { faltan.push("sintaxis moderna"); }
  if (!faltan.length) return;
  window.__compatFallo = true;
  document.addEventListener("DOMContentLoaded", function () {
    var m = /Chrome\/(\d+)/.exec(navigator.userAgent);
    if (!document.body) return;
    document.body.innerHTML =
      '<div style="font:16px/1.5 system-ui,sans-serif;max-width:420px;margin:12vh auto;padding:24px;text-align:center;color:#16223D">' +
      '<div style="width:60px;height:60px;border-radius:14px;background:#16223D;color:#E8A33D;display:inline-flex;align-items:center;justify-content:center;font-size:30px;margin-bottom:14px">!</div>' +
      '<h1 style="font-size:20px;margin:0 0 8px">Tu navegador es muy antiguo</h1>' +
      '<p>Esta aplicación necesita un navegador más reciente' + (m ? ' (el tuyo es Chrome ' + m[1] + ')' : '') + '.</p>' +
      '<p><b>Qué hacer:</b> abre Google Play, busca <b>«Android System WebView»</b> y <b>«Google Chrome»</b> y pulsa <b>Actualizar</b>. Luego vuelve a abrir la aplicación.</p>' +
      '<p style="color:#5A6784;font-size:13px">Falta: ' + faltan.join(", ") + '.</p></div>';
  });
})();

// Marca visible de entorno de pruebas (ver entorno.html): evita confundir datos de prueba con los reales.
(function () {
  try {
    var raw = localStorage.getItem("ra-entorno");
    var o = raw ? JSON.parse(raw) : null;
    if (!o || !o.url) return;
    document.addEventListener("DOMContentLoaded", function () {
      var b = document.createElement("div");
      b.setAttribute("role", "status");
      b.style.cssText = "position:fixed;top:0;left:0;right:0;z-index:99999;background:#C1443A;color:#fff;font:700 12px system-ui,sans-serif;text-align:center;padding:3px 8px;letter-spacing:.04em";
      b.textContent = "ENTORNO DE PRUEBAS · " + String(o.nombre || "pruebas").toUpperCase() + " · no son datos reales";
      if (document.body) {
        document.body.appendChild(b);
        document.body.style.paddingTop = "20px";
      }
    });
  } catch (e) { /* sin almacenamiento: no hay entorno alterno */ }
})();
