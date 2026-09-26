 # ===== 阶段 1：用 Node 构建前端 =====
  FROM node:22-alpine AS build
  WORKDIR /app
  COPY package*.json ./
  RUN npm install
  COPY . .
  RUN npm run build

  # ===== 阶段 2：用 Nginx 托管静态文件 =====
  FROM nginx:alpine
  COPY --from=build /app/dist /usr/share/nginx/html
  COPY nginx.conf /etc/nginx/conf.d/default.conf
  EXPOSE 80