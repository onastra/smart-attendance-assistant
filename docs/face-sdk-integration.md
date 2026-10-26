# 人脸识别 SDK 接入说明（模块 G）

> 本文回答两个问题：**为什么现在是模拟实现**，以及**拿到授权后要改哪些地方**。
> 面向答辩与后续接手的人，不虚构任何"已经接好了"的能力。

## 1. 当前状态

| 项 | 现状 |
|---|---|
| 配置项 `lz.face.provider` | `noop`（默认） |
| 生效实现 | `NoopFaceRecognitionService`（`lz-face` 模块） |
| 是否真实人脸检测 | **否**。只做图片字节的 SHA-256 指纹，比对的是哈希字符相同比例 |
| 响应标记 | 所有接口返回 `mock=true`；日志统一带 `[MOCK]` 前缀 |
| 原始人脸图片 | **不保存**（`lz.face.store-image=false`），只存特征值哈希 |
| 真实 SDK | 虹软（ArcSoft）授权与 AppId 当前不可得，依赖中也没有 SDK jar |

### 模拟实现到底做了什么

```
特征 = SHA256("lz-face-mock|" + SHA256(图片字节))
相似度 = 两个 64 位十六进制特征中相同位置字符数 / 64
```

- 同一张图 → 相似度 1.0 → 通过
- 换一张图 → 相似度约 0.03~0.06 → 低于阈值 0.6 → 拒绝

**它不做的**：人脸检测、关键点定位、活体检测、光照/姿态归一化、1:N 识别。
换句话说，换个人、换张照片、用屏幕翻拍都能骗过它。**它没有任何身份凭证效力**，
这也是为什么考勤记录仍然只认场所二维码打卡。

## 2. 为什么要这样做而不是"假装真的人脸识别"

三条理由，按重要性排序：

1. **生物特征不可更换**。把模拟结果包装成真实核验，一旦被当成凭证使用，
   造成的后果远超"没有这个功能"。所以 `mock` 标记写在服务层，界面与日志都藏不住。
2. **适配层本来就该先立接口**。业务代码只依赖 `FaceRecognitionService` 接口，
   换实现时改一个配置项即可，不需要动任何调用方——这一点现在已经验证过了：
   把 provider 切成 `arcsoft`，应用照常启动。
3. **接入点留在代码里，不留口头承诺**。`ArcSoftFaceRecognitionService` 已存在，
   只是运行时探测到缺 SDK 就明确报"不可用"，而不是崩溃。

## 3. 拿到授权后的改造清单

### 3.1 引入依赖

虹软 SDK 不发布在 Maven 中央仓库，需从官网下载后安装到本地仓库：

```bash
mvn install:install-file \
  -Dfile=arcsoft-sdk-face-3.0.0.0.jar \
  -DgroupId=com.arcsoft \
  -DartifactId=arcsoft-sdk-face \
  -Dversion=3.0.0.0 \
  -Dpackaging=jar
```

并在 `lz-face/pom.xml` 增加依赖（`provided` 或 `runtime` 均可，
但必须保证运行时 classpath 里有，否则 `Class.forName` 探测不到）。

> Windows 下还需要把 SDK 的 `dll`（如 `libarcsoft_face.dll`、`libarcsoft_face_engine.dll`）
> 放到 `java.library.path` 能找到的位置，或启动时加 `-Djava.library.path=...`。

### 3.2 填写配置

```yaml
lz:
  face:
    provider: arcsoft      # 关键：这一项切换实现
    app-id: <你的 AppId>
    sdk-key: <你的 SDK Key>
    store-image: false     # 保持 false：默认不落原始人脸图
```

也可以用环境变量注入，避免写进配置文件：

```bash
set LZ_FACE_PROVIDER=arcsoft
set LZ_FACE_APP_ID=xxxx
set LZ_FACE_SDK_KEY=xxxx
```

### 3.3 改造 `ArcSoftFaceRecognitionService`

当前实现为了"缺依赖也能编译"，全程用反射探测，**没有调用任何 SDK API**。
拿到 SDK 后需要把三个方法替换为真实调用：

| 方法 | 对应 SDK 能力 | 说明 |
|---|---|---|
| `extractFeature(byte[])` | `FaceEngine.extractFaceFeature` | 先 `detectFaces` 拿到人脸框，确认**只有一张人脸**再提特征；检测到 0 张或多张都应拒绝 |
| `similarity(a, b)` | `FaceEngine.compareFaceFeature` | 返回 SDK 给出的比对分数（0~1），不要再自己算哈希 |
| `threshold()` | 按 SDK 建议取值 | 虹软官方推荐 0.8 左右，需结合实际误识率校准 |

同时补充两件事，缺了会让真实实现"看起来能用但实际不安全"：

- **活体检测**（`FaceEngine` 的 RGB / IR 活体接口）。没有活体检测的人脸核验
  可以被照片攻击，这种情况下不如继续用二维码。
- **人脸质量校验**：光照过暗、姿态偏转过大时应拒绝并要求重拍，
  否则会把一张质量极差的低分特征存进库，之后永远核验不过。

### 3.4 数据迁移

`face_feature` 表已有 `provider` 与 `mock_flag` 两列，且唯一键是
`(employee_id, provider)`，所以模拟数据与真实数据天然分开存放，**不需要删表**：

```sql
-- 查看哪些是模拟数据（切换 provider 后这些不会被真实实现读到）
SELECT employee_id, provider, mock_flag FROM face_feature WHERE mock_flag = 1;

-- 确认全部切换完成后，可选择清理模拟数据
DELETE FROM face_feature WHERE provider = 'noop' AND mock_flag = 1;
```

切换后员工需要**重新录入**：模拟特征与真实特征格式不同，不能混用。

## 4. 隐私与合规边界

- 默认 `store-image=false`，只存特征值的哈希，不存原始人脸图片。
  `feature_hash` 是单向摘要，即使泄漏也无法还原成人脸图像。
- 录入与核验仅限本人；替他人操作需要 `system:user:list`（管理员）权限，
  服务层 `FaceService.resolveTargetEmployee` 强制校验，不依赖前端。
- 若将来确实需要留存原图用于申诉复核，必须单独评估存储加密、访问审计与保留期限，
  并把 `lz.face.store-image` 的默认值变更写进变更记录。

## 5. 验收对照

| 需求 | 验证方式 |
|---|---|
| REQ-G-01 接口可被替换实现 | 切 `lz.face.provider` 即可换实现，业务代码零改动 |
| REQ-G-02 响应 `mock=true` | `scripts/verify-phase5.mjs` 第 8 组断言 |
| REQ-G-03 日志含 MOCK 标记 | 启动日志 `[MOCK] 人脸识别使用模拟实现`；录入/核验日志带 `[MOCK]` |
| REQ-G-04 切换配置不崩溃 | 切 `arcsoft` 后应用正常启动，调用返回明确的"缺少 SDK / 授权"提示 |
| REQ-G-05 默认不落原图 | `/api/face/status` 返回 `storeImage=false` |
| REQ-G-06 / G-07 真实 SDK 与活体 | **受阻**：需 SDK 授权与设备，见本文第 3 节 |
