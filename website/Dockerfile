# Seren marketing site — static files served by nginx on port 80
FROM nginx:1.27-alpine

# Custom nginx config (multi-page, gzip, asset cache)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Site root
COPY public/ /usr/share/nginx/html/

# Simple 404 page (optional; falls back to nginx default if missing)
# Already included via public/404.html when present

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
