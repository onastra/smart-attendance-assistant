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
java -jar lz-app/target/lz-app.jar
```

启动后：
- 健康检查：http://localhost:8080/api/system/health
- 接口文档：http://localhost:8080/swagger-ui.html

> ⚠️ 端口被覆盖的排查：Spring 宽松绑定会把环境变量 `SERVER__PORT` 解析成 `server.port`。
> 若运行环境注入了该变量，默认端口会被改写，启动时显式指定即可：`java -jar lz-app.jar --server.port=8088`。
> 另外数据源配置了懒加载（`initial-size=0`），数据库暂不可用时应用仍会启动，
> 由 `/api/system/health` 真实探测连通性并返回错误信息，便于快速定位。

> 也可复制 `server/lz-app/src/main/resources/application-local.yml.example` 为 `application-local.yml` 填写本地配置（该文件已被 `.gitignore` 忽略）。

## 5. 前端启动

```bash
cd web
npm install
npm run dev      # http://localhost:5173
npm run build    # 生产构建
```

## 6. 项目结构

```
├── docs/            项目文档（见上表）
├── docs/sql/        数据库脚本
├── server/          后端 Maven 多模块（模块化单体）
│   ├── lz-common/       统一响应、异常、分页、工具类
│   ├── lz-framework/    Web/鉴权/异常/参数校验/OpenAPI
│   ├── lz-system/       用户、角色、权限、员工、组织
│   ├── lz-place/        办公场所、公司 ID 码、坐标适配
│   ├── lz-attendance/   二维码、打卡、考勤记录、统计
│   ├── lz-approval/     请假、加班审批
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
5. 学校原始资料（`项目总指令.docx`、`项目分析.docx`、`北游JavaEE工程实践(4周).docx`）**只读不改**。

## 8. 当前状态

阶段 0（项目审计与实施计划）已完成，详见 `docs/progress.md`。
**阻塞**：MySQL 口令未提供，数据库初始化与集成测试尚未执行。
