# 系统架构与技术选型说明

> 文档状态：阶段 0（项目审计与实施计划）产出，随实现推进更新。
> 依据优先级：**《北游JavaEE工程实践(4周).docx》（原始方案）> 《项目总指令.docx》（实施纲领）> 《项目分析.docx》（辅助分析）**。
> 本文中所有结论均按标签来源标注：`【文档明确】`、`【技术判断】`、`【工程设计】`、`【待确认】`。

---

## 1. 决策摘要（已由本人确认，2026-10-26）

| 编号 | 决策项 | 结论 | 依据 |
|---|---|---|---|
| D-1 | 前端组件库 | **Element Plus**（Vue 3 技术栈），不再使用 ElementUI | 原始方案第 15 页评估清单列出 ElementPlus；第 15 页技术选型写 Vue+ElementUI，两者冲突，需二选一 |
| D-2 | 后端架构 | **模块化单体（Modular Monolith）**，Maven 多模块 + 单一 Spring Boot 启动进程 | 原始方案未强制微服务；【技术判断】4 周单人/小组实训下微服务联调成本高、收益低 |
| D-3 | Nacos / OpenFeign / 微服务 | **已配置未启用**：依赖纳入 dependencyManagement、配置项与开关齐全，默认关闭 | 用户决策 + 【文档明确】原始方案第 11 页、第 20 天课程列出 Nacos/OpenFeign/K8s |
| D-4 | AI Agent / 企业知识库 | **可运行的真实检索实现**（MySQL 全文索引 + 中文 ngram 分词）+ **外部大模型/向量服务适配层**（无 Key 时降级为检索摘要，明确标注） | 《项目总指令》模块 F 第 6、8、9 条 |
| D-5 | 人脸识别 / 活体检测 | **接口 + 适配层 + 明确标注的模拟实现**，保留真实 SDK 接入点；界面与 API 响应必须带 `mock=true` 标记 | 《项目总指令》模块 G；虹软 SDK 授权与设备未取得 |

---

## 2. 技术选型对照表

### 2.1 后端

| 技术 | 原始方案依据 | 是否必须 | 冲突/兼容问题 | 本项目方案 |
|---|---|---|---|---|
| Java | 【文档明确】第 4 页成员基础要求 | 必须 | 无 | **JDK 17**（本机已安装 17.0.13，与 Spring Boot 3 兼容；SpringBoot 3 要求 ≥17） |
| Spring Boot | 【文档明确】技术选型"后台框架"、第 5 天课程 | 必须 | 无 | **Spring Boot 3.3.5**（本机 ~/.m2 已有该版本，离线可复现） |
| Spring MVC / Web | 【文档明确】第 5 天"SpringBoot 请求处理/返回 JSON" | 必须 | 无 | spring-boot-starter-web 3.3.5 |
| MyBatis | 【文档明确】第 4 天课程 | 必须 | 无 | mybatis-spring-boot-starter **3.0.3**（适配 Boot 3） |
| Druid | 【文档明确】第 4 天"Alibaba/Druid 连接池"、持久层清单 | 必须 | Boot 3 需专用 starter | **druid-spring-boot-3-starter 1.2.23** |
| MySQL | 【文档明确】第 3 天课程、技术清单 | 必须 | 本机为 **5.7.19**，不支持窗口函数与部分 8.0 特性 | MySQL 5.7 + **mysql-connector-j 8.3.0**（驱动兼容 5.7）；DDL 规避窗口函数 |
| Redis | 【文档明确】第 11 页数据中心、第 12 天课程 | **待确认**（本机未安装服务） | 无 Redis 时应用必须能启动 | starter 已引入、**`lz.cache.enabled` 开关默认关闭**；启用失败自动降级到本地缓存并记录 WARN |
| Nacos | 【文档明确】第 11 页数据中心、评估清单 | **非必须**（用户决策 D-3） | 与单体架构同时启用会引入不必要的注册/配置依赖 | **已配置未启用**：依赖、配置项、`NacosConfig`（`@ConditionalOnProperty`）齐全，`lz.nacos.enabled=false` |
| OpenFeign | 【文档明确】第 15 页评估清单 | 非必须（同上） | 单体内部无需 HTTP 调用自身 | 同上，默认关闭 |
| Spring Task | 【文档明确】第 11 页"定时任务，定时采集器" | 必须 | 无 | **spring-boot-starter 内置 @Scheduled**，用于过期二维码失效、考勤日统计回滚重算 |
| Maven | 【文档明确】第 2 天课程 | 必须 | 无 | Maven 3.9.6（本机）+ 多模块 reactor |
| JWT | 【工程设计】 | 《项目总指令》模块 A 第 1、7 条要求身份认证与后端鉴权 | Spring Security 学习成本高、课程未提及 | **自研 JWT + HandlerInterceptor + 自定义 @RequirePermission 注解**，BCrypt 存密码；理由：链路短、可讲解、满足"后端真实鉴权"要求 |
| OpenAPI/Swagger | 【工程设计】《项目总指令》第五.9 条 | — | Boot 3 不兼容 springfox | **springdoc-openapi 2.6.0**（Knife4j 风格 UI 可选） |

### 2.2 前端

| 技术 | 原始方案依据 | 是否必须 | 冲突/兼容问题 | 本项目方案 |
|---|---|---|---|---|
| HTML/CSS/JS | 【文档明确】第 13 页、第 6 天课程 | 必须 | 无 | 基础 |
| Node.js | 【文档明确】第 6 天课程 | 必须 | 本机 Node **22.22.2** | Vite 5 支持 Node 18+，兼容 |
| Vue | 【文档明确】第 7–8 天课程、技术选型 | 必须 | 与 ElementUI(Vue2) 冲突 | **Vue 3.4.x**（Composition API + `<script setup>`） |
| Vue Router | 【文档明确】第 8 天课程 | 必须 | 无 | vue-router 4.x |
| Pinia | 【文档明确】第 8 天课程"Pinia 状态库" | 必须 | 无 | pinia 2.x |
| Axios | 【文档明确】第 8 天、技术选型 | 必须 | 无 | axios 1.7.x + 统一拦截器 |
| ElementUI vs ElementPlus | 【文档明确但自相矛盾】技术选型写 ElementUI，评估清单写 ElementPlus | **冲突** | ElementUI 仅支持 Vue2，与 Vue3/ElementPlus 不兼容 | **Element Plus 2.8.x**（用户决策 D-1）；不混用两套组件库 |
| ECharts | 【文档明确】第 18 天课程、第 16 天数据看板 | 必须（"了解"层级） | 无 | echarts 5.5.x |
| Vite | 【工程设计】 | 课程未指定构建工具 | 无 | Vite 5.4.x（替代 vue-cli，构建更快、配置更少） |

### 2.3 移动端与高风险能力

| 能力 | 原始方案依据 | 状态 | 说明 |
|---|---|---|---|
| H5 扫码打卡页 | 【文档明确】第 9 天"移动端 H5 页面开发"、核心功能列表"登陆页面" | **必做** | 移动浏览器可访问的自适应页面 |
| uni-app / 微信小程序 | 【文档明确】第 17 天课程，目标层级为"了解" | **扩展** | 阶段 6 视时间决定是否做 uni-app 同源码编译；不把 Web 后台缩放冒充小程序 |
| 人脸识别（虹软 SDK / Face-Api） | 【文档明确】第 13–15 天课程 | **待接入（受阻）** | SDK 授权、AppId、摄像头设备均未取得 → 走 D-5 适配层 + 模拟实现 |
| 活体检测 / 人证核验 | 【文档明确】第 14 天 | **待接入（受阻）** | 同上；不宣称已实现 |
| 多模态知识库（PDF/Word/Excel/PPT/图片/音视频） | 【文档明确】第 6 页核心功能 | **分阶段必做** | 先做 TXT/MD/PDF/Word/Excel/PPT 文本提取 + 切分 + 检索；图片 OCR 与音视频转写列为扩展 |
| AI Agent | 【文档明确】项目名与介绍 | **必做（最小可用版）** | 检索式问答 + 工具调用骨架 + 来源引用；不伪造模型输出 |
| HR/ERP/OA 集成 | 【文档明确】第 6 页"集成与扩展性强" | **待确认/扩展** | 无外部系统接口 → 预留 `ExternalSystemAdapter` 接口 + 本地桩实现，不宣称真实对接 |
| 云端存储与同步 | 【文档明确】第 6 页 | **扩展/本地替代** | 先做受控的本地文件存储 + 权限校验，标注"非云端" |
| Docker / CI / K8s | 【文档明确】第 11 页 DevOps、第 19–20 天 | **待确认** | 本机 Docker daemon 未启动；先产出 Dockerfile 与 GitHub Actions工作流文件供演示，实际镜像构建待确认 |

---

## 3. 目标架构

### 3.1 运行时拓扑（模块化单体，单一进程）

```
┌──────────────┐   ┌────────────────┐
│ 管理后台 Web │   │ H5 移动端打卡  │   （Element Plus SPA / 移动自适应页）
└──────┬───────┘   └───────┬────────┘
       │  HTTP/JSON (REST) │
       └─────────┬─────────┘
                 ▼
   ┌──────────────────────────────────────────────────┐
   │  lz-app（Spring Boot 3.3.5 单一启动进程 :8080）   │
   │  ┌────────────┬─────────────┬──────────────────┐ │
   │  │ lz-system  │ lz-place    │ lz-attendance    │ │
   │  │ 用户/角色  │ 办公场所    │ 二维码/打卡/统计 │ │
   │  ├────────────┼─────────────┼──────────────────┤ │
   │  │ lz-approval│ lz-knowledge│ lz-face          │ │
   │  │ 请假/加班  │ 知识库检索  │ 人脸识别适配层   │ │
   │  ├────────────┴─────────────┴──────────────────┤ │
   │  │ lz-framework（Web/安全/异常/分页/校验/OpenAPI）│ │
   │  │ lz-common（统一响应/R、异常码、工具类）        │ │
   │  └─────────────────────────────────────────────┘ │
   └───────┬──────────────────┬──────────────┬────────┘
           ▼                  ▼              ▼
      MySQL 5.7         本地文件存储      Redis(可选,默认关)
      Druid 连接池       data/files/      Nacos(可选,默认关)
```

> 【技术判断】模块化单体 vs 微服务：原始方案 *提到* Spring Cloud/Nacos 但未 *要求* 必须拆分微服务（第 15 页为课程目标层级）。4 周周期内，单体可交付完整端到端链路，且 Maven 多模块已预留拆分边界（每个业务模块自持 controller/service/mapper/domain，跨模块只允许调用对外 Service 接口），后续可按模块升级为微服务而不重写业务代码。

### 3.2 "Nacos / 微服务：已配置未启用"的落地方式

1. `server/pom.xml` 的 `<dependencyManagement>` 中登记 `spring-cloud-alibaba-nacos-discovery`、`spring-cloud-starter-openfeign`、**不写入任何子模块 `<dependencies>`**。
2. `lz-framework` 预留 `config/NacosConditionalConfig.java`：`@Configuration @ConditionalOnProperty(name="lz.nacos.enabled", havingValue="true")`，内含服务注册与外部化配置的接入说明。
3. `application.yml` 中 `lz.nacos.enabled: false`、`lz.feign.enabled: false`；README 记录"如需启用，修改开关 + 启动 Nacos Server 2.x"。
4. `docs/progress.md` 中该能力标记为 **已配置未启用**，不算"已实现"。

### 3.3 AI 知识库架构（模块 F）

```
上传文件 → 解析器工厂(DocParser) → 文本切分器(ChunkSplitter, 重叠 200 字)
        → kb_document / kb_chunk 落库（含全文索引）→ 检索器(Retriever)
        → [可选] LlmClient 适配层（无 Key 时跳过）→ 应答器(AnswerComposer)
```

| 组件 | 实现 | 无外部依赖时的行为 |
|---|---|---|
| `DocParser` | Apache PDFBox(PDF) / Apache POI(Word、Excel、PPT) / 纯文本 | 真实解析，失败时明确报错 |
| `Retriever` | **MySQL 5.7 InnoDB FULLTEXT + ngram 解析器**（中文分词，`ngram_token_size=2`），辅以 LIKE 兜底 | **真实可用**，返回带分数的片段 |
| `EmbeddingClient` | 接口 + `NoopEmbeddingClient`（默认） | 默认关闭；启用需配置 `LZ_LLM_API_KEY` |
| `LlmClient` | 接口 + `NoopLlmClient`（默认），OpenAI 兼容协议实现待启用 | 无 Key → 返回检索摘要并在响应中标 `generatedBy: "retrieval-only"` |
| 答案约束 | 检索命中数 < 阈值 → 直接返回"知识库中没有找到足够依据"，**不允许模型自由生成** | 满足模块 F 第 6 条 |

### 3.4 人脸识别适配层（模块 G）

```
FaceRecognitionService (接口)
   ├── NoopFaceRecognitionService  ← 默认启用，标 mock=true
   └── ArcSoftFaceRecognitionService ← @ConditionalOnProperty(lz.face.provider=arcsoft)，未接入
```

- 所有返回值带 `mock` 字段；前端界面显示"模拟识别"徽章；日志打 `[MOCK]` 前缀。
- 生物特征数据：默认只存**特征值哈希与时间戳**，不落原始人脸图片（`lz.face.store-image=false`）。
- 真实接入文档见 `docs/face-sdk-integration.md`（阶段 5 产出）。

---

## 4. 分层与依赖规则

| 层 | 职责 | 禁止事项 |
|---|---|---|
| `controller` | 接收 HTTP、参数绑定、返回 VO | 不写业务规则、不直接操作 Mapper |
| `service`（接口 + Impl） | 业务规则、事务边界、权限校验 | 不感知 HttpServletRequest |
| `mapper` | MyBatis SQL 映射 | 不含业务逻辑 |
| `domain.entity` | 与表一一对应 | 不直接作为接口出入参 |
| `domain.dto` | 接口入参 | — |
| `domain.vo` | 接口出参 | — |

模块依赖方向：`lz-app → 各业务模块 → lz-framework → lz-common`。**业务模块之间禁止横向依赖**；确需跨模块时，调用对方 service 接口且须在本文档登记。

---

## 5. 目录结构

```
server/                          # Maven 多模块后端
├── pom.xml                      # parent：依赖版本统一管理（含 Nacos/Feign 的 dependencyManagement）
├── lz-common/                   # R 统一响应、BusinessException、错误码、工具类
├── lz-framework/                # Web/异常/分页/校验/鉴权拦截器/OpenAPI/MyBatis配置
├── lz-system/                   # 用户、角色、权限、员工、公司、部门、岗位
├── lz-place/                    # 办公场所、公司ID码、坐标采集适配层
├── lz-attendance/               # 二维码、打卡、考勤记录、统计
├── lz-approval/                 # 请假、加班审批
├── lz-knowledge/                # 知识库解析、切分、检索、Agent 应答
├── lz-face/                     # 人脸识别接口与适配实现
├── lz-job/                      # Spring Task 定时任务
└── lz-app/                      # 启动模块（Application.java + 各环境配置）
web/                             # Vue3 + Element Plus 前端
├── src/api/                     # Axios 封装与各模块 API
├── src/router/                  # 路由与路由守卫
├── src/stores/                  # Pinia
├── src/layouts/                 # 后台布局 / H5 布局
├── src/views/                   # 按业务模块分页
└── vite.config.js
docs/                            # 项目文档（本文档所在目录）
docs/sql/                        # 数据库脚本
```

---

## 6. 安全设计要点

1. 密码：BCrypt 加盐存储，库内不存明文；初始账号密码由脚本生成并提示首次登录修改。
2. 认证：JWT（HS256），`Authorization: Bearer`；有效期与密钥由环境变量注入，禁止硬编码。
3. 鉴权：后端拦截器 + `@RequirePermission`，**不依赖前端隐藏按钮**（《项目总指令》模块 A 第 7 条）。
4. 二维码：HMAC-SHA256 签名 + 服务端二次校验 + 有效期/一次性标记，防篡改与重放。
5. 上传文件：类型白名单、大小限制、重命名存储、不在静态目录直出，下载走鉴权接口。
6. 敏感信息：`application-local.yml`（含数据库口令）加入 `.gitignore`；正式值通过环境变量 `LZ_DB_PASSWORD` 等注入。

---

## 7. 待确认事项与已确认决议

### 7.1 已确认（2026-10-26 由项目负责人拍板）

| 编号 | 事项 | 决议 | 落地方式 |
|---|---|---|---|
| Q-1 | 请假/加班审批是否计入验收必做 | **暂缓（2026-10-26）→ 阶段 E（2026-10-27）已补齐实现** | REQ-E-01~E-07 已于阶段 E 实现（`lz-approval` 模块，复用两张预留表、未改表结构）；`biz_application` / `biz_application_approval` 已建表并打通接口与页面；E-07 统计消费待接 |

### 7.2 仍待确认（不影响当前阶段开工）

| 编号 | 事项 | 当前默认处理方式 |
|---|---|---|
| Q-2 | Redis 是否必须（本机未安装） | 默认关闭；若必须，请安装 Redis for Windows 或启用 WSL |
| Q-3 | 人脸识别是否允许"模拟 + 适配层"交付 | 暂按《项目总指令》模块 G 执行；建议向老师确认 |
| Q-4 | Docker / CI / K8s 是否需要真实演示 | 暂提供配置文件，实际运行待确认 |
| Q-5 | 实训起止日期、提交清单、文档模板 | 未知，按《项目总指令》阶段 8 产出全套文档自保 |
| Q-6 | 汇报时长与个人答辩范围 | 未知，阶段 8 产出答辩材料后按实际情况裁剪 |
