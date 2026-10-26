# =============================================================
#  后端运行镜像
# -------------------------------------------------------------
#  刻意不在镜像里做 Maven 构建：
#    1) CI/本地已经跑过 mvn package（含测试），这里重复编译既慢又无法复用测试门禁；
#    2) 构建镜像只需要"可执行的那一件产物"，分层更清晰、镜像更小。
#  因此构建前必须先执行：cd server && mvn package（产物 server/lz-app/target/lz-app.jar）
# =============================================================
FROM eclipse-temurin:17-jre-jammy

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -Dfile.encoding=UTF-8"

RUN ln -snf /usr/share/zoneinfo/${TZ} /etc/localtime && echo ${TZ} > /etc/timezone

WORKDIR /app

# 可执行 JAR（模块化单体的唯一产物）
COPY server/lz-app/target/lz-app.jar /app/lz-app.jar

# 本地受控存储目录（lz.storage.local-path 默认 ./data/files）
RUN mkdir -p /app/data/files && chmod -R 755 /app

EXPOSE 8080

# exec 形式：java 进程直接占用 PID 1，才能收到 SIGTERM 走 Spring 优雅停机，
# 否则 docker stop 会等到 10 秒超时后被强杀，可能造成正在写入的打卡记录中断。
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/lz-app.jar"]
