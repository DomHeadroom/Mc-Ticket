FROM docker.io/node:26-bookworm AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM docker.io/nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/mc-ticket/browser /usr/share/nginx/html
EXPOSE 80