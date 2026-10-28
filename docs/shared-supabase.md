# 共用一个 Supabase 数据库（双人协作）

> 场景：两名开发者在各自电脑上跑各自的后端，但**连同一个云端 PostgreSQL**——
> A 打的卡 / 建的员工 / 传的知识库，B 的后台立刻能看到，反之亦然。
> 无需同一 Wi-Fi，宿舍和家里都能连。

## 1. 原理（先看懂这段，后面不踩坑）

```
A 的浏览器 ──> A 的 Vite(5173) ──> A 的后端(8080) ──┐
                                                    ├──> 同一个 Supabase PG
B 的浏览器 ──> B 的 Vite(5173) ──> B 的后端(8080) ──┘
```

- **共享的只是数据库**，后端/前端/Redis 仍是各自本机一套（Redis 是本地缓存，不需要共享）
- 两边代码不变，只是数据源从 `127.0.0.1:5432` 换成 Supabase
- 前提已经具备：`docs/sql/postgresql/schema.sql` 本来就是按
  "PostgreSQL 15+（Supabase 实测 PG 17）"写的，`pg_trgm` 扩展 Supabase 内置，直接可用

## 2. 建库（只做一次，由一个人操作）

1. 到 [supabase.com](https://supabase.com) 注册（免费版够用），**New project**：
   - 区域选 **Tokyo / Singapore**（国内访问延迟最低，约 50–100ms）
   - 设置 Database password（记下来，微信私发给对方，**绝不进 git / 群聊**）
2. 项目页 → **SQL Editor** → 依次执行（**顺序不能换**）：
   1. `docs/sql/postgresql/schema.sql` 全文粘贴执行（⚠️ 里面有 `DROP TABLE IF EXISTS`，**只能由一个人执行一次**；另一人执行会清空所有数据）
   2. `docs/sql/postgresql/data.sql`（5 个账号 / 3 个场所 / 权限码等基础数据）
3. （可选，演示用）灌 14 天演示打卡：跑一次
   `node scripts/seed-sample-attendance.mjs`（带第 4 节的环境变量），或直接在
   SQL Editor 执行 `docs/sql/sample-attendance-14d.sql`

> 免费 vs 付费：免费版两人日常开发完全够用，唯一注意点是**项目 7 天不活跃会被暂停**，
> 被暂停后到控制台点 Restore 即可（答辩前一天记得检查）。

## 3. 生成两个"共享密钥"（只做一次）

**这一步不做，跨机器的功能会莫名失败**——QR 码和登录 token 都带签名：

- 你后台生成的场所二维码，员工手机请求可能打到对方的后端 →
  二维码 HMAC 签名密钥（`qr-sign-secret`）不一致会被判"伪造"
- 你登录拿到的 token 只在你自己的后端有效 → JWT 密钥不一致，跨后端直接 401

各生成一个随机值（Git Bash / WSL）：

```bash
openssl rand -base64 32     # → JWT 密钥
openssl rand -hex 24        # → 二维码签名密钥
```

连同 Supabase 的连接信息一起微信私发给对方：**连接串、数据库口令、两个密钥**。

## 4. 各自本机的配置（两个人都要做）

1. 复制模板：

   ```bash
   cd server/lz-app/src/main/resources
   cp application-cloud.yml.example application-cloud.yml
   ```

   （`application-cloud.yml` 已被 gitignore，口令不会随 git 泄露）

2. 填写四个值：连接串（含 `?sslmode=require`）、用户名（`postgres.<项目ref>`，
   带前缀）、数据库口令、两个共享密钥。**两台机器的密钥必须逐字一致**。

3. 启动后端时把 profile 换成 `local,cloud`（cloud 只覆盖数据源和共享密钥，
   Redis / LLM / 地图 Key 继续用你本机的 local 配置）：

   ```bash
   cd "D:/A_school_training/Project Document/server"
   java -jar lz-app/target/lz-app.jar --server.port=8080 --spring.profiles.active=local,cloud
   ```

   想切回本机 Docker 里的库，去掉 `,cloud` 即可，两套随时切换。

4. **验收**：`curl http://localhost:8080/api/system/health` 里
   `database.url` 应显示 supabase 地址且 `connected: true`；
   然后双方各自登录后台，在任意一边打一条卡，另一边刷新考勤记录应能看到。

### 连接串怎么拿

Supabase 控制台 → 顶部 **Connect** → 选 **Session pooler**（端口 5432，
域名形如 `aws-0-ap-northeast-1.pooler.supabase.com`）。

⚠️ **不要**用 `db.<项目ref>.supabase.co` 直连域名：免费版那是 IPv6-only，
普通家用网络连不上；pooler 域名走 IPv4。也别用 Transaction pooler（端口 6543），
它不支持 JDBC 长连接会话语义。

## 5. 命令行脚本怎么连 Supabase

回归脚本、种子脚本都认 `LZ_PG_*` 环境变量，`sslmode` 默认就是 `require`，直接可用：

```bash
export LZ_PG_HOST=aws-0-ap-northeast-1.pooler.supabase.com
export LZ_PG_PORT=5432
export LZ_PG_DB=postgres
export LZ_PG_USER=postgres.xxxxxxxxxxxxxxx
export LZ_PG_PWD=你的数据库口令
# LZ_PG_SSLMODE 默认 require，连 Supabase 不用改
npm run verify
```

## 6. 已知的行为差异（不是 bug）

| 现象 | 原因 | 处理 |
|---|---|---|
| 对方刚打完卡，我这边统计数字没变 | 两边各有本地 Redis 缓存，A 写库不会失效 B 的缓存，要等 TTL（统计 60s / 场所详情 600s） | 等一分钟，或重启自己后端 |
| 偶尔操作慢半拍 | 数据库在海外机房，往返 50–100ms | 正常，演示无感 |
| 隔了一周连不上 | Supabase 免费版 7 天不活跃自动暂停 | 控制台点 Restore |
| 两人同时跑日汇总重算 | 定时任务两边都会触发 | 重算是幂等的（覆盖写），无害 |
| **启动后第一次 health 显示 `connected=false`，刷新一次又变 true** | 云端库**首次**连接要做 TLS 握手 + JVM 安全随机数初始化，实测 5 秒出头，超过 `application.yml` 里本地库用的 `max-wait: 5000` | `application-cloud.yml`（模板已含）把 `spring.datasource.druid.max-wait` 放宽到 `15000`。之后连接稳定在 1~2 秒 |

## 7. 安全边界

- 数据库口令、两个共享密钥：**微信私发，不进 git、不进群聊、不写进任何入库文件**
  （模板 `.example` 里只允许占位符）
- Supabase 免费版自带 `supabase_rls` 等内部 schema，别动它们；
  我们只用 `public` schema，与本地 PG 完全一致
- 答辩后若要下线：控制台 Delete project 即可，本地 Docker 库不受任何影响
