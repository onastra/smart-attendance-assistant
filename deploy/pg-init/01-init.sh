#!/bin/bash
# =============================================================
#  PostgreSQL 容器首次初始化
# -------------------------------------------------------------
#  由 PostgreSQL 官方镜像在 /docker-entrypoint-initdb.d 下自动执行
#  （仅首次，即数据卷为空时；卷里已有数据则跳过）。
#
#  顺序：
#    postgresql/schema.sql  建表（DROP ... CASCADE + CREATE，可重复执行）
#      ↓
#    postgresql/data.sql    基础数据（组织/账号/权限码，带显式主键 id）
#      ↓
#    sample-attendance-14d.sql  14 天演示打卡流水
#
#  与 MySQL 版的关键差异：
#    1) 不需要再逐个跑 patch-phase2~6 / phaseE —— PG 版 schema.sql 已把
#       所有增量补丁内联，一次执行即为最终结构；
#    2) 演示打卡流水不在 data.sql 里，必须单独补，
#       否则统计看板与趋势图全是 0（实测踩过：3 项断言因此失败）；
#    3) 知识库中文检索依赖 pg_trgm 扩展，schema.sql 开头会自动建。
#
#  只挂载、不复制：SQL 仍以 docs/sql 为唯一事实来源，避免容器里再存副本后走样。
# =============================================================
set -e

DB="${POSTGRES_DB:-lianzhi}"
USER="${POSTGRES_USER:-postgres}"

# 同一份脚本要同时服务两种挂载形态：
#   Docker Compose —— bind mount 整个 docs/sql，保留 postgresql/ 子目录
#   Kubernetes     —— ConfigMap 的 key 不允许含 "/"，只能平铺成 pg-schema.sql
# 所以按顺序探测，谁存在用谁。
SCHEMA=/seed/postgresql/schema.sql
[ -f "$SCHEMA" ] || SCHEMA=/seed/pg-schema.sql
DATA=/seed/postgresql/data.sql
[ -f "$DATA" ] || DATA=/seed/pg-data.sql

echo "[init] 开始导入 PostgreSQL 数据库（$DB）..."

echo "[init] 1/3 建表结构（$(basename "$SCHEMA")）..."
psql -v ON_ERROR_STOP=1 -U "$USER" -d "$DB" -f "$SCHEMA"

echo "[init] 2/3 基础数据（$(basename "$DATA")）..."
psql -v ON_ERROR_STOP=1 -U "$USER" -d "$DB" -f "$DATA"

echo "[init] 3/3 演示打卡流水（sample-attendance-14d.sql）..."
if [ -f /seed/sample-attendance-14d.sql ]; then
  psql -v ON_ERROR_STOP=1 -U "$USER" -d "$DB" -f /seed/sample-attendance-14d.sql
else
  echo "[init] 未找到演示流水脚本，跳过（看板会是空的）"
fi

echo "[init] 数据库初始化完成。"
psql -U "$USER" -d "$DB" -tAc "SELECT 'tables=' || count(*) FROM information_schema.tables WHERE table_schema='public';"
