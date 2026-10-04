// Aplica el tema guardado antes del primer pintado (evita el parpadeo claro→oscuro). Script clásico, bloqueante a propósito.
(function () {
  try {
    var t = localStorage.getItem("cv-theme");
    if (t) document.documentElement.setAttribute("data-theme", t);
    else if (matchMedia("(prefers-color-scheme: dark)").matches) document.documentElement.setAttribute("data-theme", "dark");
  } catch (e) { /* sin acceso a storage: queda el tema claro */ }
})();
