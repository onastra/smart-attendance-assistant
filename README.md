# 联智 · 企业级 AI Agent 智能考勤与业务助手

> 学校实训项目｜Vue 3 + Element Plus 前端 / Spring Boot 3 模块化单体后端 / MySQL 5.7（兼容 PostgreSQL / Supabase）
>
> 一套面向中小企业的智能考勤中台：二维码扫码打卡、人脸识别打卡、考勤统计看板、请假加班审批、AI 知识库问答，开箱即用。

## ✨ 核心功能

| 模块 | 说明 |
|---|---|
| 🕐 二维码打卡 | 管理端签发动态签名二维码，员工 H5 扫码打卡，防截图转发（签名 + 时效 + 位置围栏） |
| 😊 人脸识别打卡 | 虹软 ArcFace SDK 真实活体检测（照片/翻拍拒绝）；未配置 SDK 时自动降级为感知哈希模拟模式，响应带 `mock: true` 标记 |
| 📊 统计看板 | 出勤率 / 迟到早退 / 平均工时 / 趋势预测 / 员工画像，全部来自数据库真实聚合 |
| 📝 请假加班审批 | 审批流 + 额度核算（加班 1:1 折调休、余额校验），已批请假自动计入日汇总 |
| 🤖 AI 知识库 | 文档上传解析、全文 + 语义混合检索（RRF 融合）、LLM 多轮流式问答（SSE）与来源引用 |
| 🗺️ 地图围栏 | 高德地图选址 + 半径圈可视化，Key 未配置时诚实降级为手动录入 |
| 🔐 权限体系 | RBAC（管理员/主管/员工三角色），知识库文档支持全员/部门/仅自己三级可见范围 |

所有外部依赖（LLM、地图、人脸 SDK、Redis、Nacos）均开关化，未配置时自动降级、不崩溃、不假装可用。

## 🚀 快速开始

**环境要求**：JDK 17+ · Maven 3.6+ · Node.js 18+ · MySQL 5.7+（或 PostgreSQL 15+，脚本在 `docs/sql/postgresql/`）

```bash
git clone https://github.com/onastra/smart-attendance-assistant.git
cd smart-attendance-assistant

# 1) 建库导数据（口令按提示输入；增量补丁按文件名顺序执行）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS lianzhi DEFAULT CHARACTER SET utf8mb4"
mysql -uroot -p --default-character-set=utf8mb4 lianzhi < docs/sql/schema.sql
mysql -uroot -p --default-character-set=utf8mb4 lianzhi < docs/sql/data.sql
for f in docs/sql/patch-phase*.sql; do mysql -uroot -p --default-character-set=utf8mb4 lianzhi < "$f"; done

# 2) 私密配置：复制模板并填入自己的数据库口令与 JWT 密钥（已被 .gitignore 忽略）
cp server/lz-app/src/main/resources/application-local.yml.example \
   server/lz-app/src/main/resources/application-local.yml

# 3) 后端（打包 + 启动，默认 8080；脚本自动处理 profile 与端口）
cd server && mvn clean package -DskipTests && cd ..
scripts\start-backend.cmd          # Windows；Linux 用 java -jar，参数见 docs/setup-guide.md

# 4) 前端（默认 5173，/api 已代理到 8080）
cd web && npm ci && npm run dev
```

浏览器打开 http://localhost:5173 即为管理后台；接口文档 http://localhost:8080/swagger-ui/index.html。

**演示账号**：`admin / Admin@123`（管理员）· `manager01 / Admin@123`（主管）· `LZ0001 / Emp@123`（员工）。新建员工时系统自动开通登录账号（工号即账号，初始密码 `Emp@123`）。

## 📁 项目结构

```
├── docs/            项目文档（见下表）；sql/ 建表脚本与补丁；samples/kb/ 知识库样例
├── server/          后端 Maven 多模块（模块化单体，11 个模块）
│   ├── lz-system / lz-place / lz-attendance / lz-approval   人事·场所·考勤·审批
│   ├── lz-knowledge / lz-face / lz-file                     AI 知识库·人脸·文件
│   └── lz-framework / lz-common / lz-app                    框架·公共·启动模块
├── web/             Vue3 前端（管理后台 + 2 个 H5 移动端页）
├── deploy/          Docker Compose / K8s / Nginx 配置
└── scripts/         启动脚本与端到端回归脚本
```

## 📚 文档导航

| 文档 | 内容 |
|---|---|
| `docs/introduction.md` | 项目简介：一页看懂是什么、能做什么 |
| `docs/architecture.md` | 技术选型、模块边界、AI/人脸适配层设计 |
| `docs/setup-guide.md` | 详细启动指南（含依赖清单与排错表） |
| `docs/deploy-server.md` | 生产部署指南（systemd + Nginx + HTTPS） |
| `docs/api-design.md` | 接口清单与统一响应/错误码约定 |
| `docs/database-design.md` | 23 张表的字段、索引与 ER 关系 |
| `docs/test-report.md` | 测试报告：全量回归结果与缺陷清单 |
| `docs/acceptance.md` | 验收清单 + 十分钟演示脚本 |
| `docs/user-manual.md` | 用户手册（管理员/主管/员工三视角） |
| `docs/defense-qa.md` | 答辩问答题库 |
| `docs/known-issues.md` | 已知问题与限制 |
| `docs/reference/` | 学校原始资料只读存档 |

## ✅ 质量

- **单元测试 64 项 + 接口回归 177 项 = 241 项全通过**，一条命令跑完全量回归：

```bash
export LZ_MYSQL_PWD=<数据库口令>
npm run verify
```

- 统计数据全部来自数据库聚合；模拟实现一律显式标记（`mock: true` / 界面徽章 / `[MOCK]` 日志），不伪造第三方调用。
