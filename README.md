# 联智·企业级 AI Agent 智能考勤与业务助手

> 学校实训项目｜依据《北游 JavaEE 软件项目开发工程实践（4 周）》方案实施
> 架构决策：**Vue 3 + Element Plus 前端 / Spring Boot 3 模块化单体后端 / MySQL 5.7**
> Nacos 与微服务组件**已配置未启用**；AI 知识与人脸识别采用**真实检索/接口 + 明确标注的模拟适配层**。

---

## 1. 文档导航

| 文档 | 内容 |
|---|---|
| `docs/architecture.md` | 技术选型对照、目标架构、模块边界、AI/人脸适配层设计、待确认事项 |
| `docs/requirements-matrix.md` | 61 条需求（编号 REQ-X-NN），含依据、优先级、实现状态 |
| `docs/database-design.md` | 23 张表的字段、索引、约束与 ER 关系 |
| `docs/api-design.md` | 统一响应/分页/错误码与全模块接口清单 |
| `docs/development-plan.md` | 阶段 0–8 实施计划与依赖关系 |
| `docs/test-plan.md` | 测试用例清单与执行记录规则 |
| `docs/progress.md` | 当前完成情况、阻塞项与下一步 |
| `docs/sql/` | 数据库建表脚本与演示数据 |
| `docs/reference/` | **原始资料只读存档**（`北游JavaEE工程实践(4周).docx`、`项目总指令.docx`、`项目分析.docx`）+ 优先级说明 |

---

## 2. 环境要求（本机实测 2026-10-26）

| 组件 | 要求 | 本机实际 |
|---|---|---|
| JDK | 17+ | 17.0.13 ✅ |
| Maven | 3.6+ | 3.9.6 ✅ |
| Node.js | 18+ | 22.22.2 ✅ |
| MySQL | 5.7+ | 5.7.19（端口 3306，服务运行中）✅ |
| Redis | 可选（默认关闭） | 未安装 ➖ |
| Docker | 可选（部署演示用） | daemon 未启动 ➖ |
| Git | 2.x | 2.55.0 ✅ |

## 3. 数据库初始化

```bash
# 创建数据库（口令按提示输入，或改用环境变量注入）
mysql -h127.0.0.1 -P3306 -uroot -p -e "CREATE DATABASE IF NOT EXISTS lianzhi DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"

# 建表
mysql -h127.0.0.1 -P3306 -uroot -p lianzhi < docs/sql/schema.sql

# 演示数据（虚构员工，不含真实个人信息）
mysql -h127.0.0.1 -P3306 -uroot -p lianzhi < docs/sql/data.sql
```

## 4. 后端启动

```bash
cd server

# 数据库连接信息用环境变量注入，禁止写入代码库
export LZ_DB_URL="jdbc:mysql://127.0.0.1:3306/lianzhi?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false"
export LZ_DB_USERNAME=root
export LZ_DB_PASSWORD=<你的口令>
export LZ_JWT_SECRET=<32位以上随机串>

mvn -U clean package -DskipTests
# finalName 固定为 lz-app
cd ..
scripts\start-backend.cmd
# 或手动：java -jar server\lz-app\target\lz-app.jar --server.port=8080
```

启动后：
- 健康检查：http://localhost:8080/api/system/health
- 接口文档：http://localhost:8080/swagger-ui/index.html

> ⚠️ **端口被篡改的已知坑（真实踩过）**：Spring 宽松绑定会把环境变量 `SERVER__PORT` 解析成 `server.port`。
> 本机 Agent 终端曾注入 `SERVER__PORT=56043`，导致默认 8080 被静默改写。
> 因此请使用 `scripts/start-backend.cmd`（脚本会先清空 `SERVER__PORT` / `SERVER_PORT` / `PORT` 再显式传 `--server.port`），
> 或手动启动时始终显式带端口。`LZ_SERVER_PORT=8088 scripts\start-backend.cmd` 可换端口。
>
> 另外数据源配置了懒加载（`initial-size=0`），数据库暂不可用时应用仍会启动，
> 由 `/api/system/health` 真实探测连通性并返回错误信息，**不会因为配不出库就崩，也不会假装连上了**。

> 本地私密配置：复制 `server/lz-app/src/main/resources/application-local.yml.example` 为 `application-local.yml`
> 填写数据库口令（该文件已被 `.gitignore` 忽略，绝不入库）。

## 5. 前端启动

```bash
cd web
npm install
npm run dev      # http://localhost:5173
npm run build    # 生产构建
```

开发期 Vite 已配置 `/api` 代理到 `http://localhost:8080`（默认，可用 `LZ_API_TARGET` 覆盖），无需处理跨域。

**联调自测命令**（验证"前端 → 代理 → 后端"整条链路）：

```bash
curl -s http://localhost:5173/api/system/health
```

应返回后端真实 JSON（不是空、不是错误页）。返回体中的 `database.connected` 反映真实连通性。

## 6. 项目结构

```
├── docs/            项目文档（见导航表）
│   ├── sql/         数据库建表脚本与演示数据
│   └── reference/   原始资料只读存档（docx）
├── server/          后端 Maven 多模块（模块化单体）
│   ├── lz-common/       统一响应、异常、分页、工具类
│   ├── lz-framework/    Web/鉴权/异常/参数校验/OpenAPI
│   ├── lz-system/       用户、角色、权限、员工、组织
│   ├── lz-place/        办公场所、公司 ID 码、坐标适配
│   ├── lz-attendance/   二维码、打卡、考勤记录、统计
│   ├── lz-approval/     请假、加班审批 ⏸ 暂缓（Q-1 决议，无业务代码）
│   ├── lz-knowledge/    知识库解析、切分、检索、问答
│   ├── lz-face/         人脸识别接口与模拟实现
│   ├── lz-job/          Spring Task 定时任务
│   └── lz-app/          启动模块（唯一可执行 JAR）
└── web/             前端 Vue3 + Element Plus
```

## 7. 重要的诚实性约定（本项目红线）

1. 不伪造第三方调用：地图、云存储、HR/ERP/OA、AI 模型、人脸 SDK 未配置时必须显式提示"未启用"。
2. 模拟实现必须标记：响应含 `mock: true`，界面显示"模拟识别"徽章，日志带 `[MOCK]` 前缀。
3. 统计数据必须来自数据库聚合；演示数据必须带 `dataSource=SAMPLE` 标记。
4. 不允许通过跳过测试、关闭校验、吞异常制造构建成功。
5. 学校原始资料（`docs/reference/` 下三份 docx）**只读不改**，需求有歧义时以其为准。
6. **Q-1 决议（2026-10-26）**：模块 E《请假/加班审批》7 条需求**暂缓实现**，优先保证核心考勤闭环。数据库表保留作为结构预留，不做半截实现混入验收。

## 8. 当前状态（2026-10-26）

| 项 | 状态 |
|---|---|
| 阶段 0 项目审计与实施计划 | ✅ 完成（7 份文档） |
| 阶段 1 项目骨架与开发环境 | 🟡 骨架成型，差 1 项 |
| Git 版本管理 | ✅ 已 `git init`，首次提交 `685f9b1`（64 文件） |
| 原始资料归档 | ✅ 3 份 docx 已移入 `docs/reference/` 只读存档 |
| 后端 `mvn package` | ✅ BUILD SUCCESS |
| 前端 `npm run build` | ✅ built in 8.06s |
| `/api/system/health`、`/swagger-ui/index.html` | ✅ 均 200 |
| **数据库初始化** | 🔴 **阻塞 BK-1**：MySQL 口令已确认提供、**尚未送达** |

**阶段 1 剩余唯一缺口**：MySQL 口令 → 执行 `docs/sql/schema.sql` + `data.sql` → 验证 `database.connected=true` → 启动 dev server 完成前后端联调。

收到口令后即刻进入**阶段 2（认证鉴权 + 员工/组织/办公场所 CRUD）**。

详见 `docs/progress.md`。
