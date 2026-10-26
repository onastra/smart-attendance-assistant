# 部署与环境指南

本文记录本机真实跑通的三种运行形态，以及每一条的验证方式。
所有命令都在本项目里实际执行过；未跑通的部分会明确标注「未实跑」，不做"写了就算做了"的陈述。

---

## 0. 三种形态与端口约定

| 形态 | 前端 | 后端 | MySQL | Redis | 用途 |
|------|------|------|-------|-------|------|
| 本机直跑（默认） | 5173 | 8080 | 3306 | 6379 | 日常开发、答辩演示 |
| Docker Compose | 18081 | 18080 | 13306 | 16379 | 一键起全套、验证容器化 |
| Kubernetes | 30081（NodePort） | ClusterIP `backend:8080` | ClusterIP `mysql:3306` | ClusterIP `redis:6379` | 验证 K8s 清单可用 |

端口刻意错开：容器形态不抢占本机 3306/6379/8080/5173，两套可以同时跑。

---

## 1. Redis（已安装并注册为 Windows 服务）

**安装位置**：`D:\tools\redis`（Redis 5.0.14.1，tporadowski Windows 移植版）
**服务名**：`Redis-Lianzhi`，开机自启

```
配置：D:\tools\redis\redis.service.conf
数据：D:\tools\redis\data
日志：D:\tools\redis\logs\redis-service.log
端口：127.0.0.1:6379（仅回环）
口令：lianzhi_dev_redis（本机开发用弱口令，见 application-local.yml）
```

**常用命令**

```bat
sc query Redis-Lianzhi                       :: 查看状态
net stop Redis-Lianzhi / net start Redis-Lianzhi
D:\tools\redis\redis-cli.exe -a lianzhi_dev_redis --no-auth-warning ping
D:\tools\redis\redis-cli.exe -a lianzhi_dev_redis --no-auth-warning keys lz:cache:v1:*
```

重装或迁移到别的机器：以管理员运行 `D:\tools\redis\install-redis-service.bat`。

**安全设置（刻意做的两件事）**：只监听回环地址；`FLUSHALL` / `FLUSHDB` 被重命名禁用，
避免演示时一把清掉全部缓存却没有任何提示。

---

## 2. 缓存开关与降级行为

配置项（`server/lz-app/src/main/resources/application.yml`）：

```yaml
lz:
  cache:
    enabled: ${LZ_CACHE_ENABLED:false}      # 关闭时应用完全不依赖 Redis
    key-prefix: ${LZ_CACHE_KEY_PREFIX:lz:cache:v1:}
    ttl:
      place-detail-seconds: 600
      statistics-summary-seconds: 60
```

三态行为，**任何一态下接口结果都正确，只是快慢不同**：

| 状态 | 表现 |
|------|------|
| `enabled=false` | `NoopCacheService`：每次直查，启动打一条 WARN，不做"内存 Map 假装缓存" |
| `enabled=true` + Redis 正常 | `RedisCacheService`：真实读写，`/api/system/health` 里 `cache.reachable=true` |
| `enabled=true` + Redis 挂了 | 单次请求降级直查；连续失败 3 次进入 30 秒熔断（不再等连接超时）；恢复后自动退出熔断 |

已接入缓存的两处：

1. **统计看板概览** `statistics:summary:*`，TTL 60 秒。响应带 `cached` 与 `generatedAt`，
   让"数字来自缓存"这件事可见，而不是看起来像数据没更新。
2. **场所详情** `place:detail:<id>`，TTL 10 分钟，编辑/停用/重发场所码时主动失效。

缓存键带数据范围（`dept=`/`emp=`）：否则部门主管可能拿到管理员视角缓存下来的全量数字，
等同于越权。

**重算后必须失效**：`DailyStatService.rebuild()` 结束会 `evictByPrefix("statistics:")`。
这是实测发现的缺陷——管理员点了"重新计算"，看板在 TTL 内仍显示旧数字，会被当成重算没生效。

---

## 3. Docker Compose（已实跑）

### 3.0 把 Docker 数据盘迁出 C 盘（本机已执行）

WSL2 后端的 Docker 把所有镜像、容器、数据卷放在一个虚拟磁盘里：

```
C:\Users\<你>\AppData\Local\Docker\wsl\disk\docker_data.vhdx
```

**这个 vhdx 只增不减**——删掉容器和镜像后文件也不会缩小。本机实测它涨到 **14 GB**，
是 C 盘告急的主因（容器实际占用 8.1GB 镜像 + 3.7GB 数据卷 + 2.5GB 构建缓存）。

迁移办法：**Docker Desktop → Settings → Resources → Advanced → Disk image location**，
改成 `D:\Docker\wsl\disk` 后 Apply & Restart，Docker 会自动搬完，**镜像与数据卷全部保留**
（本机实测：迁移前 C 盘可用 11 GB，迁移后 **24 GB**，19 个镜像 0 丢失）。

> 注意网上流传的 `wsl --export docker-desktop-data` 教程对新版 Docker Desktop **不适用**——
> 新版已不再有独立的 `docker-desktop-data` 发行版，数据统一在上述 vhdx 里。

### 3.1 启动与停止

镜像重建前必须先在本机打包 JAR，否则容器里用的还是上一版产物
（`mvn package` 若报 `Unable to rename lz-app.jar`，说明本机 8080 的 java 进程正占用它，先停掉即可）：

```bash
cd server && mvn package
cd ../deploy && docker compose up -d --build backend
```
```bash
cd deploy
docker compose up -d --build     # 首次构建约 1-2 分钟（前端 npm ci 占大头）
docker compose ps                # 四个容器，mysql/redis/backend 应为 healthy
docker compose logs -f backend
docker compose down -v           # 连数据卷一起清掉，下次会重新初始化
```

- 后端镜像只装可执行 JAR（构建前先 `cd server && mvn package`），不在镜像里重复编译，
  也就不可能绕过 CI 的测试门禁。
- MySQL 用 `mysql:5.7` 并加 `--ngram_token_size=2`：中文全文检索按 2 字切词，与知识库检索口径一致。
- 初始化脚本 `deploy/mysql-init/01-init.sh` **按时间顺序**导入：
  `schema → data → patch2 → patch3 → patch4 → patch5 → patch6(文件模块) → 14 天演示打卡`。
  顺序不能颠倒：增量脚本用 `INSERT IGNORE` 追加权限码，若先跑会占掉 `AUTO_INCREMENT` 的 id，
  `data.sql` 再按显式 id 插入就主键冲突（实测 ERROR 1062）。
- **首次部署后必须跑一次日汇总重算**，否则看板是全 0 —— 初始化只导入了打卡流水，
  `att_daily_stat` 是空的（实测：99 条流水 / 0 条汇总，概览返回 `personDays=0`）：
  ```bash
  curl -X POST http://localhost:18080/api/statistics/daily-rebuild -H "Authorization: Bearer <token>"
  ```
  不带日期参数时会**自动按"最早一条打卡流水 ~ 今天"推导区间**。
  这一点是 2026-10-26 实测修掉的缺陷：原实现无参数只重算当天，而演示流水落在过去 14 天，
  结果管理员点了重算、看板依旧全 0，验收现场会被读成"统计模块没做"。

---

## 4. Kubernetes（清单产出 + 实跑记录）

清单在 `deploy/k8s/`，按顺序编号：

```
00-namespace.yaml   lianzhi 命名空间
10-configmap.yaml   非敏感配置（DB/Redis 地址、人脸/地图/大模型开关）
20-secret.yaml      口令与密钥（演示值，正式部署必须外部注入）
30-mysql.yaml       Deployment + PVC + Service（含 ngram 参数与初始化挂载）
40-redis.yaml       Deployment + Service（纯缓存，不落盘）
50-backend.yaml     Deployment + Service（Service 名必须是 backend）
60-frontend.yaml    Deployment + NodePort 30081
deploy.sh           一键部署/卸载
```

部署：

```bash
cd deploy/k8s && ./deploy.sh          # 部署（等待全部 rollout 完成）
./deploy.sh --delete                  # 卸载（保留 PVC，避免误删数据库）
```

两条易踩的坑已经写进清单注释：

1. `backend` 这个 Service 名不能改——Nginx 里是 `proxy_pass http://backend:8080`，
   compose 与 K8s 共用同一份前端镜像。
2. SQL 不能整体挂进 `/docker-entrypoint-initdb.d`：镜像按文件名排序执行，
   `data.sql` 会跑到 `schema.sql` 前面。所以脚本挂成单独文件，SQL 挂到 `/seed` 由脚本按序导入。

### 4.1 实跑结果：Docker Desktop 内置 K8s 未能启用，改用 kind

**结论先说**：Docker Desktop 内置 Kubernetes 在本机**没有跑起来**，原因是镜像拉取，不是清单问题。
为了让"K8s 真跑"这件事落地，改用 kind（跑在同一个 Docker 引擎上的真实集群）完成部署验证。

内置 K8s 的失败过程（如实记录，便于复现或换环境重试）：

1. `registry.k8s.io` 直连不可达（实测 `docker pull registry.k8s.io/pause:3.10` 连接超时）；
2. 于是把 K8s 镜像从阿里云 `google_containers` 预拉并打回官方 tag——但内置 K8s
   用的是另一套名字（`desktop-kubernetes-pause:3.10`），阿里云上不存在，拉取失败；
3. 也试过设置 `KubernetesImagesRepository` 指向阿里云，映射规则对不上，同样失败。

**kind 实跑步骤**（本机已跑通，集群名 `lianzhi`）：

```bash
# 镜像：kindest/node 走 docker.1ms.run 加速源拉取
docker pull docker.1ms.run/kindest/node:v1.31.0
kind create cluster --name lianzhi --image docker.1ms.run/kindest/node:v1.31.0 --wait 300s
kind get kubeconfig --name lianzhi > ~/.kube/config

# 应用镜像导入（必须用 --provenance=false 构建，否则带 attestation 的镜像 ctr 导入会失败）
docker build --provenance=false -f deploy/backend.Dockerfile  -t lianzhi/backend:1.0.0  .
docker build --provenance=false -f deploy/frontend.Dockerfile -t lianzhi/frontend:1.0.0 .
kind load docker-image lianzhi/backend:1.0.0 lianzhi/frontend:1.0.0 --name lianzhi

# 中间件镜像：多平台镜像 kind load 会失败，改为在节点内拉取后改回官方名字
docker exec -e HTTP_PROXY= -e HTTPS_PROXY= lianzhi-control-plane \
  ctr -n k8s.io images pull docker.1ms.run/library/mysql:5.7
docker exec lianzhi-control-plane ctr -n k8s.io images tag \
  docker.1ms.run/library/mysql:5.7 docker.io/library/mysql:5.7     # redis:7-alpine 同理

cd deploy/k8s && ./deploy.sh
```

**实跑结果**（2026-10-26）：四个 Pod 全部 `1/1 Running`；后端 health 返回
`db=true`、`cache.mode=redis`、`cache.reachable=true`；
登录后 `daily-rebuild` 重算 14 天 / 70 行，`summary` 返回 `personDays=70`、
`attendanceRate=98.0`、`dataSource=mixed`；前端与 `/api` 反代均 200。

**kind 的两个注意事项**：

- NodePort **不会**自动映射到宿主（需 `extraPortMappings` 重建集群），
  所以本机验证用端口转发：`kubectl -n lianzhi port-forward svc/frontend 18091:80`。
- 节点内容器会继承宿主的 `HTTP_PROXY`，而宿主的 127.0.0.1 代理在容器里不可达，
  拉取镜像时要显式清空代理环境变量。

### 4.2 踩到的两个真实缺陷（已写进清单注释）

1. **Redis 口令没生效**：`args` 里写 `$(REDIS_PASSWORD)` 时 kubelet 没有展开成实际口令，
   readiness 一直 `WRONGPASS`。改用 `sh -c '... "$REDIS_PASSWORD" ...'` 由容器内 shell 展开。
2. **探针里的 `$(VAR)` 会被 kubelet 展开成空串**：probe 的 exec command 里
   `$(REDIS_PASSWORD)` 展开结果为空（与 shell 语法冲突），同样要写成 `$REDIS_PASSWORD`。

---

## 5. CI（GitHub Actions + act 本地实跑）

流水线：`.github/workflows/ci.yml`

- `backend`：`mvn -B -ntp package`（**不跳过测试**，产物上传）
- `frontend`：`npm ci` + `npm run build`
- `images`：依赖前两个 job 的产物，构建两个镜像（不推送）

本地实跑（与云端同一份 YAML，本机已跑通）：

```bash
export DOCKER_HOST=tcp://127.0.0.1:2375        # act 连不上 Windows 命名管道，必须走 TCP
MSYS_NO_PATHCONV=1 /d/tools/act/act.exe -j backend --action-offline-mode \
  -P ubuntu-latest=node:22-bookworm --container-architecture linux/amd64 \
  --container-options "-v D:/apache-maven-3.9.6-bin/apache-maven-3.9.6:/opt/maven \
                       -v D:/apache-maven-3.9.6-bin/apache-maven-3.9.6/mvn_resp:/opt/mvn_resp"

MSYS_NO_PATHCONV=1 /d/tools/act/act.exe -j frontend --action-offline-mode \
  -P ubuntu-latest=node:22-bookworm --container-architecture linux/amd64
```

**实跑结果**（2026-10-26）：

- `backend`：`BUILD SUCCESS`，耗时约 2 分钟，✅ Job succeeded
- `frontend`：`npm ci` + `vite build` 通过，✅ Job succeeded
- `images` job 依赖产物上传，act 下没有 `ACTIONS_RUNTIME_TOKEN`，这三个步骤本地跳过

为了让 act 能跑，workflow 里加了三处**只在 act 下生效**的处理，都已注明原因：

1. Maven 不存在（GitHub runner 预装、act 镜像没有）→ 优先用挂载进来的宿主 Maven；
2. 宿主 Maven 的 `localRepository` 是 Windows 路径 → 容器内写一份用户级 `settings.xml` 覆盖；
3. 上传/下载产物需要 `ACTIONS_RUNTIME_TOKEN` → 本地跳过，云端照常。

另需 `--action-offline-mode`：代理对 `git-upload-pack` 返回 502 时，
用已缓存的 actions 直接跑，不重新 clone。

---

## 6. 已知限制（如实列出）

- 容器内 Redis 用 `redis:7-alpine`，本机 Windows 服务是 Redis 5.0.14.1：
  仅缓存用途，命令集没有差异，但版本不一致这件事本身记录在案。
- K8s 里 MySQL 是单副本 Deployment（不是 StatefulSet + 主从）：
  适用于演示与功能验证，**不是生产级数据库拓扑**。
- 镜像没有推到任何仓库，K8s 用 `imagePullPolicy: IfNotPresent` 依赖本地构建；
  换机器必须先把镜像 load 进去或推到私有仓库。
- Nacos 仍是"已配置未启用"（`lz.nacos.enabled=false`），
  启动脚本见 `deploy/nacos/start-nacos.cmd`，启用需要额外引入注册/配置中心依赖，本项目未做。
