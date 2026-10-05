FROM node:24.21.0-alpine AS build

WORKDIR /app

COPY package*.json ./

RUN npm ci

COPY . .

RUN npm run build -- --configuration production \
    && SITE_DIR="$(dirname "$(find dist -type f -name index.html | head -n 1)")" \
    && test -n "$SITE_DIR" \
    && mkdir -p /site \
    && cp -a "$SITE_DIR"/. /site/


FROM nginx:stable-alpine

COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /site/ /usr/share/nginx/html/

RUN mkdir -p /usr/share/nginx/html/pdf/local

EXPOSE 80

HEALTHCHECK \
  --interval=30s \
  --timeout=5s \
  --start-period=10s \
  --retries=3 \
  CMD wget -qO- http://127.0.0.1/health >/dev/null || exit 1