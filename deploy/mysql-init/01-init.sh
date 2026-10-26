#!/bin/bash
# =============================================================
#  MySQL 容器首次初始化
# -------------------------------------------------------------
#  由 MySQL 官方镜像在 /docker-entrypoint-initdb.d 下自动执行（仅首次，数据卷为空时）。
#
#  顺序不是随便排的：
#    schema.sql 建表（DROP + CREATE，可重复执行）
#      ↓
#    data.sql 基础数据（组织/账号/权限码，带显式主键 id）
#      ↓
#    各阶段增量脚本（INSERT IGNORE + 条件 DDL，幂等）
#      ↓
#    14 天演示打卡（可选）
#
#  为什么 data.sql 必须在增量脚本之前：
#    增量脚本用 INSERT IGNORE 追加新权限码，靠 perm_code 唯一键去重，
#    若它们先跑，会先占掉 AUTO_INCREMENT 的 id=1..N，
#    data.sql 再按显式 id 插入就主键冲突（实测 ERROR 1062）。
#
#  只挂载、不复制：SQL 仍以 docs/sql 为唯一事实来源，避免容器里再存一份副本后走样。
# =============================================================
set -e

echo "[init] 开始导入数据库（$MYSQL_DATABASE）..."

echo "[init] 1/7 建表 schema.sql ..."
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < /seed/schema.sql

echo "[init] 2/7 基础数据 data.sql ..."
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < /seed/data.sql

echo "[init] 3/7 增量 patch-phase2-permission.sql ..."
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < /seed/patch-phase2-permission.sql

echo "[init] 4/7 增量 patch-phase3-attendance.sql ..."
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < /seed/patch-phase3-attendance.sql

echo "[init] 5/7 增量 patch-phase4-statistics.sql ..."
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < /seed/patch-phase4-statistics.sql

echo "[init] 6/7 增量 patch-phase5-knowledge.sql ..."
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < /seed/patch-phase5-knowledge.sql

# 14 天演示打卡流水：默认导入（看板趋势需要数据），设 SKIP_SAMPLE=yes 可跳过
if [ "${SKIP_SAMPLE:-no}" = "yes" ]; then
    echo "[init] 7/7 跳过 14 天演示打卡（SKIP_SAMPLE=yes）"
else
    echo "[init] 7/7 导入 14 天演示打卡 sample-attendance-14d.sql ..."
    mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < /seed/sample-attendance-14d.sql
    # 日汇总不在 SQL 里算：重算逻辑在应用侧（幂等 upsert），
    # 部署后调用一次 POST /api/statistics/rebuild 即可，避免两处实现口径漂移。
    echo "[init] 注意：日汇总需部署后调用 POST /api/statistics/rebuild 重算"
fi

echo "[init] 数据库初始化完成"
