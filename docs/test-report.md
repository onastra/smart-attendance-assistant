# 测试报告

> 最后更新：2026-10-26（阶段 7）
> 执行方式：`npm run verify`（一条命令跑完全量回归）
> 本文件中的数字均为真实执行结果，非计划值。

---

## 1. 测试策略

三层，各自解决不同问题：

| 层次 | 手段 | 解决什么 |
|---|---|---|
| 单元测试 | JUnit 5 + Mockito（`mvn -B test`） | 核心算法与**安全判定**的正确性——这些错了不会被用户立刻发现，但后果严重 |
| 接口回归 | Node 脚本（`scripts/verify-phase*.mjs`） | 端到端业务链路，含**各类失败场景**（越权、篡改、重复、过期） |
| 构建门禁 | `mvn package`（不允许 `-DskipTests`） | 测试不过就打不出 jar，从流程上堵死"先上线再补测试" |

**为什么把安全相关的判定写成单元测试**：缓存键、文件可见范围、二维码签名这三类都是"写错也能正常返回结果"的逻辑
——功能测试会发现不了，只有针对性断言才能守住。

---

## 2. 单元测试（64 项，全部通过）

```
------------- 按类 -------------
lz-framework  TokenServiceTest           9 项
lz-attendance QrSignerTest               5 项
              QrPayloadTest              6 项
              GeoDistanceUtilTest        6 项
              StatisticsServiceTest     11 项
              StatisticsControllerTest   5 项
lz-knowledge  ChunkSplitterTest         10 项
lz-file       FileServiceTest           12 项
--------------------------------
合计                               64 项   失败 0
```

### 2.1 各测试类守护的内容

| 测试类 | 守护点 | 为什么必须测 |
|---|---|---|
| `TokenServiceTest` | 过期/篡改/换密钥/短密钥四类必须拒绝 | 令牌是鉴权入口，任一漏洞都会让后续权限判定形同虚设 |
| `QrSignerTest` | HMAC-SHA256 确定性；改任一字段即失效；空签名不放行 | 二维码是公开信道，改一个字符还能通过就等于任意场所可伪造 |
| `QrPayloadTest` | 畸形输入解析要有原因、不能空指针 | 用户扫到的可能是任何东西（别的系统的码、网址） |
| `GeoDistanceUtilTest` | 用"纬度 1°≈111km""京沪≈1067km"做锚点；坐标缺失返回 null | 算错 200 米就能把合规打卡误判成外勤；缺失返回 0 会让未配坐标的场所放行所有人 |
| `StatisticsServiceTest` | **缓存键必须含部门/员工**；演示数据标注三档口径 | 缓存键不含数据范围 → 部门主管命中管理员全量缓存 = 越权读取 |
| `StatisticsControllerTest` | 无参重算必须全量；显式单日不擅自扩大 | 防止"点重算没反应"这类体验缺陷回归 |
| `ChunkSplitterTest` | 窗口/重叠/短块合并/索引连续/不丢字 | 切碎了答不出语义，丢字了来源引用对不上 |
| `FileServiceTest` | 三档可见范围；DEPT 缺部门时**收紧**为仅上传人；`file:admin` 豁免 | 越权读取文件；"缺字段"必须默认收紧而不是默认公开 |

### 2.2 一条被测试抓出来的真实认知偏差

写 `StatisticsServiceTest` 时，我断言"部分演示数据"的 `dataSource` 是 `mixed`，
执行失败后才发现实现是：`sampleDays >= personDays → sample`，只有"部分是演示且部分是真实"才标 `mixed`。

**是我的预期错了，不是代码的错。** 修正断言并补了一条 `fullySampleDataIsMarkedAsSample` 专项用例，
把这个口径锁死——以后有人改了业务逻辑，这里会立刻报出来。

---

## 3. 接口回归（128 项，全部通过）

```
套件                          通过    失败
------------------------------ ----  ----
阶段 3 二维码打卡闭环            30     0
阶段 4 统计与看板                32     0
阶段 5 知识库与人脸识别           46     0
阶段 6 文件受控存储与鉴权         20     0
------------------------------ ----  ----
合计                           128     0
```

### 覆盖的失败场景（重点）

| 场景 | 期望 | 结果 |
|---|---|---|
| 重复提交打卡 | 明确原因码，不重复落库 | ✅ |
| 二维码已过期 | 拒绝 | ✅ |
| 篡改二维码签名 | 拒绝 | ✅ |
| 非本场所打卡 | 拒绝 | ✅ |
| 二维码已作废 | 拒绝 | ✅ |
| 超出围栏半径 | 拒绝并回显实测距离 | ✅ |
| 一次性码二次使用 | 拒绝 | ✅ |
| 普通员工查他人考勤 | 40301 | ✅ |
| 部门主管触发重算 | 40301（仅管理员） | ✅ |
| 越权下载 PRIVATE 文件 | 拒绝并写 `log_operation` | ✅ |
| 跨部门下载 DEPT 文件 | 拒绝 | ✅ |
| 列表接口泄露存储路径 | 不允许 | ✅ `storagePath` 已置空 |
| 日期倒序 / 非法趋势粒度 | 40001 | ✅ |

### 执行注意事项

脚本必须带 MySQL 环境变量才会清理上一轮数据：

```bash
export LZ_MYSQL_BIN="D:/gjcmysql/mysql-5.7.19-winx64/bin/mysql.exe"
export LZ_MYSQL_PWD=<口令>
npm run verify
```

不带 `LZ_MYSQL_PWD` 时脚本仍会跑，但因 5 分钟去重窗口 / 上传去重会出现**假失败**——
看起来像功能坏了，实际是数据没清。这一点在 `progress.md` 里已经踩过并记录。

---

## 4. 容器与部署验证

不是"产出清单就行"，是**真的跑过**：

| 环境 | 验证内容 | 结果 |
|---|---|---|
| Docker Compose | 四容器 healthy；Nginx 反代 → 后端；文件上传下载一致 | ✅ |
| 卷持久化 | 删掉 backend 容器重建，文件仍完整可下载 | ✅ |
| Kubernetes（kind v1.31.0） | 四 Pod `1/1 Running`；port-forward 业务链路通 | ✅ |
| CI（GitHub Actions + act） | 本地实跑两个 job（backend BUILD SUCCESS / frontend build 成功） | ✅ |

---

## 5. 本次测试发现并修复的真实缺陷

按发现时间倒序：

| # | 缺陷 | 后果 | 修复 |
|---|---|---|---|
| 1 | `daily-rebuild` 无参只重算当天 | 首次部署后 `att_daily_stat` 为空、**看板全 0**，会被当成统计模块没做 | 自动按"最早一条打卡流水 ~ 今天"推导区间 |
| 2 | 容器未挂载文件卷 | 容器重建后文件丢失，`sys_file` 记录还在 | compose named volume + K8s PVC 1Gi |
| 3 | 中文文件名导致 `Content-Disposition` 整头丢弃 | 下载文件名乱码/丢失 | ASCII 化 + `filename*=UTF-8''` 双写法 |
| 4 | 知识库文档下载可见范围硬编码 PRIVATE | DEPT 可见的文档反而下不了原文 | 文件可见范围跟随文档 |
| 5 | 重算后统计缓存未失效 | 管理员点重算，看板 TTL 内仍显示旧数字 | `evictByPrefix("statistics:")` |
| 6 | 容器初始化 SQL 顺序颠倒 | 主键冲突 ERROR 1062 | 改为 schema → data → patch → sample |
| 7 | `DocumentView` 漏带 `fileId` | 前端拿不到下载入口 | 补字段 |
| 8 | EMPLOYEE 能上传却不能删自己的文件 | 权限不自洽 | 补 `file:delete` |
| 9 | `FaceCheckin.vue` 登录传参错误 | 人脸 H5 页登录必然失败 | 改为对象传参 |

---

## 6. 测试覆盖的诚实边界

以下内容**没有**自动化覆盖，需要人工验收时确认：

- 前端 UI 交互与视觉效果（无 E2E 测试，靠手工操作 walkthrough）
- 移动端真机兼容性（只在 Chrome 设备模拟器 + 响应式规则层面验证）
- 并发压力（未做压测，仅确认逻辑正确性）
- 真实人脸识别（无 SDK 授权，走模拟适配层）
- 真实大模型生成（无 Key，走 retrieval-only）

这些边界在 `known-issues.md` 中同样如实列出。
