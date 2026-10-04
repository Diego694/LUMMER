FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/security-headers.conf
COPY index.html sw.js manifest.webmanifest /usr/share/nginx/html/
COPY assets /usr/share/nginx/html/assets
COPY estudiante /usr/share/nginx/html/estudiante
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
