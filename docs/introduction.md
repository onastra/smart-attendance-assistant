# 联智 · 智能考勤助手（Smart Attendance Assistant）

> 一句话介绍：**面向中小企业的考勤管理 + AI 知识库问答一体化平台** —— 管考勤、看数据、问制度，开箱即用。
>
> 仓库地址：https://github.com/onastra/smart-attendance-assistant

---

## 1. 这是一个什么项目？

企业里HR最头疼的两件事：**考勤数据乱** 和 **制度问答烦**。这个项目把两件事装进一个系统：

- **考勤侧**：管理员维护组织/员工/办公场所，生成动态二维码，员工用手机 H5 扫码或人脸打卡，考勤记录自动汇总成多维度统计看板；
- **AI 侧**：把公司制度文档（考勤制度、请假规定等）上传进知识库，系统自动解析切分，员工直接用**自然语言提问**（比如"路上堵车晚到会扣钱吗"），AI 基于文档内容生成带来源引用的回答，支持多轮追问和流式输出。

它是学校实训产出，但按企业工程标准实施：192 项回归测试、6 条诚实性红线、密钥零入库。

## 2. 核心功能一览

| 模块 | 能力 | 说明 |
|---|---|---|
| 组织与账号 | 用户 / 角色 / 权限（RBAC） | 后端拦截器 + `@RequirePermission` 注解鉴权，不依赖前端隐藏按钮；BCrypt 加盐存密码 |
| 考勤打卡 | **动态二维码打卡闭环** | 二维码带 HMAC-SHA256 签名 + 有效期 + 一次性标记，防篡改防重放；手机 H5 扫码即打 |
| 考勤打卡 | **人脸打卡** | 适配层设计：当前为明确标注的模拟实现（界面带"模拟识别"徽章），真实 SDK 接入点已预留 |
| 数据看板 | 出勤统计 / 趋势 / 部门对比 / 打点热力 | ECharts 可视化，全部来自数据库真实聚合，支持定时任务幂等重算 |
| 知识库 | 多格式文档管理 | 支持 TXT / MD / PDF / Word / Excel / PPT 解析，切分落库，权限可见性隔离 |
| **AI 智能问答** | **RAG 检索增强生成** | 见下节 |

### AI 智能问答（本项目亮点）

```
提问 → 关键词检索(MySQL全文/ngram) ┐
                                     ├→ RRF 融合排序 → LLM 生成 → SSE 流式输出
      语义向量检索(bge-large-zh)   ┘                      ↓
                                              来源引用 + "无依据不回答"兜底
```

- **混合检索**：全文关键词 + 语义向量双路召回，RRF 融合。问"堵车迟到扣钱吗"能命中只写了"迟到处罚"的原文条款——纯关键词检索做不到；
- **多轮对话**：会话与消息独立存储，支持追问（"年假有几天？"→"那工作满12年呢？"能正确理解指代）；
- **流式输出**：SSE 逐字推送，打字机效果，可中途停止；
- **反编造约束**：检索命中不足时明确回答"资料中没有足够依据"，不允许模型自由发挥，每条引用标注 `[1][2]` 来源。

## 3. 技术栈

| 层 | 选型 |
|---|---|
| 前端 | Vue 3.4（Composition API）+ Element Plus 2.8 + Pinia + Vue Router + ECharts 5 + Vite 5 |
| 后端 | Spring Boot 3.3 模块化单体（11 个 Maven 模块）+ MyBatis 3 + Druid 连接池 |
| 数据库 | MySQL 5.7（23 张表，含知识库全文索引与向量列） |
| 认证 | 自研 JWT（HS256）+ BCrypt + 后端注解式鉴权 |
| AI 服务 | 硅基流动 SiliconFlow（OpenAI 兼容协议）：Qwen2.5-7B-Instruct（生成）+ BAAI/bge-large-zh-v1.5（1024 维向量） |
| 文档解析 | Apache PDFBox / Apache POI |
| 可选组件 | Redis（默认关闭）、Nacos/OpenFeign（已配置未启用）、Docker/K8s/CI 配置齐备 |

**为什么是模块化单体？** Maven 多模块划清了业务边界（模块间禁止横向依赖，跨模块只走 Service 接口），4 周周期内交付完整端到端链路，后续可按模块平滑升级为微服务而不重写业务代码。

## 4. 快速开始

**环境要求**：JDK 17+、Maven 3.6+、Node.js 18+、MySQL 5.7+。

```bash
# ① 建库导数据（幂等脚本，含演示数据）
mysql -h127.0.0.1 -uroot -p -e "CREATE DATABASE IF NOT EXISTS lianzhi DEFAULT CHARACTER SET utf8mb4;"
mysql -h127.0.0.1 -uroot -p lianzhi < docs/sql/schema.sql
mysql -h127.0.0.1 -uroot -p lianzhi < docs/sql/data.sql

# ② 起后端（复制 application-local.yml.example 填数据库口令，或用环境变量注入）
cd server && mvn package -DskipTests
scripts\start-backend.cmd        # http://localhost:8080

# ③ 起前端
cd web && npm install && npm run dev   # http://localhost:5173
```

**演示账号**：

| 账号 | 口令 | 角色 |
|---|---|---|
| `admin` | `Admin@123` | 系统管理员 |
| `manager01` | `Admin@123` | 部门主管 |
| `emp01` ~ `emp03` | `Emp@123` | 普通员工 |

> AI 问答需要配置大模型 API Key（硅基流动注册即送额度，Qwen2.5-7B 与 bge 向量模型均免费）。未配置时系统自动降级为"检索-only"模式：只返回原文片段与来源，不伪造生成内容。

## 5. 工程亮点

1. **诚实性红线**：第三方能力（AI、人脸、云存储）未配置时显式提示"未启用"而不是假装成功；模拟实现一律带 `mock: true` 标记；
2. **192 项回归测试**：一条 `npm run verify` 命令跑完 64 项单元测试 + 128 项接口回归；
3. **安全落地**：密钥/口令全部走环境变量或 gitignore 的本地配置，仓库内零明文敏感信息；二维码防重放、文件鉴权下载、上传类型白名单；
4. **可观测的降级链路**：数据库连不上应用不崩（懒连接 + 健康探测），LLM 缺 Key 降级检索模式，Redis 缺席降级本地缓存——每一层失败都有明确反馈。

## 6. 目录结构

```
├── docs/            全套工程文档（架构、需求矩阵、API 设计、测试报告、答辩题库等 16 份）
│   └── sql/         建表脚本 + 演示数据
├── server/          Spring Boot 3 后端（11 个 Maven 模块，模块化单体）
│   ├── lz-system        用户/角色/权限/员工/组织
│   ├── lz-place         办公场所
│   ├── lz-attendance    二维码/打卡/统计
│   ├── lz-knowledge     知识库 + AI 问答（RAG 核心）
│   ├── lz-face          人脸识别适配层
│   ├── lz-file          文件存储
│   ├── lz-job           定时任务
│   └── ...              common / framework / approval / app
└── web/             Vue 3 + Element Plus 前端（管理后台 + H5 打卡页）
```

## 7. 已知边界（诚实声明）

- 人脸识别为**模拟实现**（虹软 SDK 授权未取得，接入改造清单见 `docs/face-sdk-integration.md`）；
- 请假/加班审批模块**已实现**（阶段 E 补齐，复用预留表结构，未改表结构；E-07 统计消费待接）；
- AI 生成使用免费的 7B 模型，复杂算术偶有瑕疵，可在配置中一键切换更强模型；
- Nacos/OpenFeign/Docker/K8s 为"已配置未启用"状态，不含微服务拆分。

## 8. 想了解更多？

| 我想知道… | 看这里 |
|---|---|
| 怎么启动、常见报错 | `docs/setup-guide.md` |
| 系统怎么设计的 | `docs/architecture.md` |
| 有哪些功能、做没做完 | `docs/requirements-matrix.md`（61 条需求逐条可查） |
| 测试做得怎么样 | `docs/test-report.md`（192 项回归） |
| 十分钟看懂演示 | `docs/acceptance.md` |
| 用户怎么操作 | `docs/user-manual.md` |
| 还有什么问题 | `docs/known-issues.md` |
