# =============================================================
#  前端镜像：构建阶段产出静态文件，运行阶段只留 Nginx
# =============================================================
FROM node:22-alpine AS build

# 国内网络下 npm 官方源经常超时，构建阶段统一走镜像源（仅影响构建，不影响运行时）
RUN npm config set registry https://registry.npmmirror.com

WORKDIR /build

# 先只拷贝依赖清单：依赖不变时这一层可以命中缓存，不必每次 npm ci
COPY web/package.json web/package-lock.json ./
RUN npm ci

COPY web/ ./
RUN npm run build

FROM nginx:1.27-alpine

# 默认站点配置：SPA 路由回退 + /api 反代到后端
# 后端服务名在 compose 与 K8s 中都叫 backend，因此同一份配置两处通用
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
