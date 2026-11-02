# 人脸识别 SDK 接入说明（模块 G）

> 现状：**已接入虹软 ArcFace V3.0 真实 SDK 并完成端到端验证**；
> 保留 `noop` 模拟实现作为无授权环境下的兜底（所有结果强制打 `mock=true` 标记）。
> 本文记录怎么跑起来、踩过哪些坑、还差什么，不虚构任何未实现的能力。

## 1. 当前状态

| 项 | 现状 |
|---|---|
| 配置项 `lz.face.provider` | `arcsoft`（本机 `application-local.yml`）；默认值仍是 `noop` |
| 生效实现 | `ArcSoftFaceRecognitionService`（真实 SDK）/ `NoopFaceRecognitionService`（兜底） |
| 人脸检测 | **是**，虹软 Engine（IMAGE 模式，`ASF_OP_ALL_OUT` 全角度） |
| 特征提取 | **是**，1032 字节特征向量 → Base64 存 `face_feature.feature_hash`（TEXT） |
| 1:1 比对 | **是**，`compareFaceFeature` 分数 0~1，阈值 0.8 |
| 活体检测 | **未启用**：需单独授权「单目 RGB 活体」SDK，未授权时开启会导致引擎初始化失败 |
| 原始人脸图片 | **不保存**（`lz.face.store-image=false`），只存特征向量 |
| 授权有效期 | 2027-04（`getActiveFileInfo`：start=2026-10-31，end=2027-10-31 区间） |

### 模拟实现（noop）到底做了什么

```
特征 = SHA256("lz-face-mock|" + SHA256(图片字节))
相似度 = 两个 64 位十六进制特征中相同位置字符数 / 64
```

同一张图 → 1.0 通过；换一张图 → 约 0.03~0.06 拒绝。它**没有人脸检测、没有身份凭证效力**，
只用于没有 SDK 授权时保证接口链路可演示。

## 2. 本地部署步骤（换机器必看）

### 2.1 放置 SDK（不入库，`.gitignore` 已忽略 `/server/libs/arcface/`）

1. 虹软开放平台下载 **ArcFace V3.0 Windows x64 Java** 包，解压到
   `server/libs/arcface/`，保证目录结构为：

```
server/libs/arcface/libs/arcsoft-sdk-face-3.0.0.0.jar
server/libs/arcface/libs/WIN64/libarcsoft_face.dll
server/libs/arcface/libs/WIN64/libarcsoft_face_engine.dll
server/libs/arcface/libs/WIN64/libarcsoft_face_engine_jni.dll
```

2. 把 SDK jar 装进本地 Maven 仓库（只需一次）：

```bash
cd server/libs/arcface/libs
mvn install:install-file -Dfile=arcsoft-sdk-face-3.0.0.0.jar \
  -DgroupId=com.arcsoft -DartifactId=arcsoft-sdk-face \
  -Dversion=3.0.0.0 -Dpackaging=jar
```

### 2.2 配置授权参数（`application-local.yml`，已 gitignore）

```yaml
lz:
  face:
    provider: arcsoft
    app-id: "<你的 APP_ID>"
    sdk-key: "<你的 SDK_KEY>"
    sdk-lib-path: "D:/.../server/libs/arcface/libs/WIN64"
    threshold: 0.8
```

其余键（`min-face-pixels`、`min-face-ratio`、`store-image`）见 `application.yml` 注释。

### 2.3 启动（工作目录有讲究）

**必须让进程工作目录为 `server/lz-app`**——虹软 SDK 把激活文件 `ArcFace64.dat`
写在**进程当前工作目录**：

- `scripts/start-backend.cmd` 已 `pushd` 到该目录；
- VS Code 任务已设 `cwd: ${workspaceFolder}/server/lz-app`；
- 手工启动前先 `cd server/lz-app`。

首次激活成功后会生成 `ArcFace64.dat`（约 1.2KB）。该文件**与本机设备绑定**，
换机器必须重新在线激活；不要拷贝给同学用，也不要入库。

## 3. 实测结果（2026-11-02）

| 场景 | 结果 |
|---|---|
| 同一张人脸图核验 | `passed=true`，`similarity=1.0` |
| 不同人的照片 | `passed=false`，`similarity=0.4297`（阈值 0.8，区分度充足） |
| 非人脸图（图标 png） | 40001「未检测到人脸，请正对镜头、保证光线充足后重拍」 |
| 员工自助核验（LZ0001 不带 employeeId） | 走本人档案，`mock=false`，通过 |
| `/api/face/status` | `available=true`、`mock=false`、`storeImage=false` |

### 踩过的坑（按出现顺序）

1. **`feature_hash VARCHAR(128)` 装不下真实特征**：Base64 后约 1376 字符 → 已改 `TEXT`
   （补丁 `docs/sql/postgresql/patch-face-feature-column.sql`，幂等）。
2. **在线激活返回 94212（NETWORK_UNKNOWN）**：激活域名 `free.as.arcsoftai.com:80` 抖动，
   稍后重试即成功；引擎做了 60 秒冷却重试，无需重启应用。
3. **激活返回 90130（SERVER_UNKNOWN_ERROR）**：激活文件写在了别的进程工作目录，
   后端找不到就重复联网激活触发服务器限流。把 `ArcFace64.dat` 放到后端工作目录即可。
4. **首次激活会占用「1 台终端」免费额度**：同一台机器重复激活是安全的（返回
   `90114 ALREADY_ACTIVATED`），换机器才会再占一个。

## 4. 安全与质量边界

- **强制单脸**：检测到 0 张或 ≥2 张人脸直接拒绝——多人画面下"挑一张脸"可能比中别人。
- **质量门槛**：人脸框短边 < 80px 或占画面 < 1% 时拒绝并要求重拍，
  避免把低质量特征存进库导致之后永远核验不过。
- **不落原图**：只存特征向量；`store-image` 保持 `false`。
- **权限**：录入/核验默认只能操作本人；替他人操作需 `system:user:list`（管理员），
  服务层 `FaceService.resolveTargetEmployee` 强制校验。
- **活体缺口（答辩要主动说明）**：当前未启用 RGB 活体，理论上照片翻拍可通过。
  接入方式：申请「单目 RGB 活体」免费 SDK → `ArcSoftFaceRecognitionService` 打开
  `setSupportLiveness(true)` 并在核验前校验 `getLiveness` 分数。

## 5. 验收对照

| 需求 | 验证方式 |
|---|---|
| REQ-G-01 接口可被替换实现 | 切 `lz.face.provider` 即可换实现，业务代码零改动 |
| REQ-G-02 模拟结果必须带 `mock=true` | `noop` 下所有响应 `mock=true`；`arcsoft` 下 `false` |
| REQ-G-03 日志标注 | `noop` 日志带 `[MOCK]`；`arcsoft` 日志带 `[FACE]` 维度/人脸框信息 |
| REQ-G-04 缺 SDK 不崩溃 | 切 `arcsoft` 但缺授权参数时，应用正常启动，接口返回 50301 与明确原因 |
| REQ-G-05 默认不落原图 | `/api/face/status` 返回 `storeImage=false` |
| REQ-G-06 真实 SDK 比对 | 同上表实测：本人 1.0 通过 / 他人 0.43 拒绝 |
| REQ-G-07 活体检测 | **未完成**：需单独授权 RGB 活体 SDK |

## 6. 数据迁移

`face_feature` 唯一键是 `(employee_id, provider)`，`noop` 与 `arcsoft` 数据天然隔离：

```sql
-- 查看模拟数据（切换 provider 后真实实现不会读到）
SELECT employee_id, provider, mock_flag FROM face_feature WHERE mock_flag = 1;

-- 确认切换完成后可清理
DELETE FROM face_feature WHERE provider = 'noop' AND mock_flag = 1;
```

切换 provider 后员工需**重新录入**：模拟特征是哈希，真实特征是特征向量，两者不能混用。
