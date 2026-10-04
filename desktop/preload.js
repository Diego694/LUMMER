// Marca al programa de escritorio: la web usa `window.escritorio` para mostrar el selector de modo online/local.
const { contextBridge } = require("electron");
contextBridge.exposeInMainWorld("escritorio", { plataforma: process.platform, version: process.env.npm_package_version || "" });
