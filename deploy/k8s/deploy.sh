#!/usr/bin/env bash
# =============================================================
#  部署到 Kubernetes（Docker Desktop 内置集群或任意集群）
# -------------------------------------------------------------
#  用法（Git Bash / Linux / macOS）：
#    cd deploy/k8s && ./deploy.sh
#    ./deploy.sh --delete      # 卸载（保留 PVC，避免误删数据库）
#
#  说明：SQL 与初始化脚本通过 ConfigMap 挂载进 MySQL Pod，
#  不复制进镜像——docs/sql 始终是唯一事实来源。
# =============================================================
set -euo pipefail

NS=lianzhi
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="$HERE/../../docs/sql"

if [[ "${1:-}" == "--delete" ]]; then
  kubectl delete -f "$HERE/60-frontend.yaml" --ignore-not-found
  kubectl delete -f "$HERE/50-backend.yaml" --ignore-not-found
  kubectl delete -f "$HERE/40-redis.yaml" --ignore-not-found
  kubectl delete -f "$HERE/30-mysql.yaml" --ignore-not-found
  kubectl delete -f "$HERE/20-secret.yaml" --ignore-not-found
  kubectl delete -f "$HERE/10-configmap.yaml" --ignore-not-found
  kubectl -n "$NS" delete configmap lianzhi-sql lianzhi-mysql-init --ignore-not-found
  echo "[k8s] 已卸载（PVC 保留，需连同数据删除请手动：kubectl -n $NS delete pvc mysql-data）"
  exit 0
fi

echo "[k8s] 1/5 命名空间 ..."
kubectl apply -f "$HERE/00-namespace.yaml"

echo "[k8s] 2/5 SQL 与初始化脚本 ConfigMap ..."
kubectl create configmap lianzhi-sql -n "$NS" \
  --from-file=schema.sql="$SQL_DIR/schema.sql" \
  --from-file=data.sql="$SQL_DIR/data.sql" \
  --from-file=patch-phase2-permission.sql="$SQL_DIR/patch-phase2-permission.sql" \
  --from-file=patch-phase3-attendance.sql="$SQL_DIR/patch-phase3-attendance.sql" \
  --from-file=patch-phase4-statistics.sql="$SQL_DIR/patch-phase4-statistics.sql" \
  --from-file=patch-phase5-knowledge.sql="$SQL_DIR/patch-phase5-knowledge.sql" \
  --from-file=sample-attendance-14d.sql="$SQL_DIR/sample-attendance-14d.sql" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create configmap lianzhi-mysql-init -n "$NS" \
  --from-file=01-init.sh="$HERE/../mysql-init/01-init.sh" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "[k8s] 3/5 配置与密钥 ..."
kubectl apply -f "$HERE/10-configmap.yaml"
kubectl apply -f "$HERE/20-secret.yaml"

echo "[k8s] 4/5 工作负载 ..."
kubectl apply -f "$HERE/30-mysql.yaml"
kubectl apply -f "$HERE/40-redis.yaml"
kubectl apply -f "$HERE/50-backend.yaml"
kubectl apply -f "$HERE/60-frontend.yaml"

echo "[k8s] 5/5 等待就绪 ..."
kubectl -n "$NS" rollout status deployment/mysql --timeout=420s
kubectl -n "$NS" rollout status deployment/redis --timeout=180s
kubectl -n "$NS" rollout status deployment/backend --timeout=420s
kubectl -n "$NS" rollout status deployment/frontend --timeout=180s

echo
kubectl -n "$NS" get pods,svc
echo
echo "[k8s] 部署完成。访问： http://localhost:30081"
echo "[k8s] 日汇总需调用一次：kubectl -n $NS port-forward svc/backend 18080:8080 后"
echo "      POST http://127.0.0.1:18080/api/statistics/daily-rebuild?dateFrom=...&dateTo=..."
