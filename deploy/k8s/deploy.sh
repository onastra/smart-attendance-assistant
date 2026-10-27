#!/usr/bin/env bash
# =============================================================
#  部署到 Kubernetes（Docker Desktop 内置集群或任意集群）
# -------------------------------------------------------------
#  用法（Git Bash / Linux / macOS）：
#    cd deploy/k8s && ./deploy.sh
#    ./deploy.sh --delete      # 卸载（保留 PVC，避免误删数据库）
#
#  说明：SQL 与初始化脚本通过 ConfigMap 挂载进 PostgreSQL Pod，
#  不复制进镜像——docs/sql 始终是唯一事实来源。
#
#  ConfigMap 的 key 不允许含 "/"，所以 PG 的 SQL 平铺成 pg-schema.sql / pg-data.sql，
#  由同一个 deploy/pg-init/01-init.sh 探测后按序导入（它同时服务 Compose 的目录形态）。
# =============================================================
set -euo pipefail

NS=lianzhi
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="$HERE/../../docs/sql"

if [[ "${1:-}" == "--delete" ]]; then
  kubectl delete -f "$HERE/60-frontend.yaml" --ignore-not-found
  kubectl delete -f "$HERE/50-backend.yaml" --ignore-not-found
  kubectl delete -f "$HERE/40-redis.yaml" --ignore-not-found
  kubectl delete -f "$HERE/30-postgres.yaml" --ignore-not-found
  kubectl delete -f "$HERE/20-secret.yaml" --ignore-not-found
  kubectl delete -f "$HERE/10-configmap.yaml" --ignore-not-found
  kubectl -n "$NS" delete configmap lianzhi-sql lianzhi-pg-init --ignore-not-found
  echo "[k8s] 已卸载（PVC 保留，需连同数据删除请手动：kubectl -n $NS delete pvc postgres-data）"
  exit 0
fi

echo "[k8s] 1/5 命名空间 ..."
kubectl apply -f "$HERE/00-namespace.yaml"

echo "[k8s] 2/5 SQL 与初始化脚本 ConfigMap ..."
# PG 版 schema.sql 已把 patch-phase2~6 与 phaseE 全部内联，不需要再逐个挂补丁。
# 演示打卡流水不在 data.sql 里，必须单独挂，否则统计看板恒为 0。
kubectl create configmap lianzhi-sql -n "$NS" \
  --from-file=pg-schema.sql="$SQL_DIR/postgresql/schema.sql" \
  --from-file=pg-data.sql="$SQL_DIR/postgresql/data.sql" \
  --from-file=sample-attendance-14d.sql="$SQL_DIR/sample-attendance-14d.sql" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create configmap lianzhi-pg-init -n "$NS" \
  --from-file=01-init.sh="$HERE/../pg-init/01-init.sh" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "[k8s] 3/5 配置与密钥 ..."
kubectl apply -f "$HERE/10-configmap.yaml"
kubectl apply -f "$HERE/20-secret.yaml"

echo "[k8s] 4/5 工作负载 ..."
kubectl apply -f "$HERE/30-postgres.yaml"
kubectl apply -f "$HERE/40-redis.yaml"
kubectl apply -f "$HERE/50-backend.yaml"
kubectl apply -f "$HERE/60-frontend.yaml"

echo "[k8s] 5/5 等待就绪 ..."
kubectl -n "$NS" rollout status deployment/postgres --timeout=420s
kubectl -n "$NS" rollout status deployment/redis --timeout=180s
kubectl -n "$NS" rollout status deployment/backend --timeout=420s
kubectl -n "$NS" rollout status deployment/frontend --timeout=180s

echo
kubectl -n "$NS" get pods,svc
echo
echo "[k8s] 部署完成。访问： http://localhost:30081"
echo "[k8s] 日汇总需调用一次：kubectl -n $NS port-forward svc/backend 18080:8080 后"
echo "      POST http://127.0.0.1:18080/api/statistics/daily-rebuild?dateFrom=...&dateTo=..."
