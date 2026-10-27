-- =====================================================================
-- 阶段 6：AI Agent 增强补丁（语义检索 + 多轮会话）
-- 执行方式：mysql -h 127.0.0.1 -uroot -p lianzhi < patch-phase6-agent.sql
-- 前提：schema.sql 与 patch-phase5-knowledge.sql 已执行
-- =====================================================================

-- 1) kb_chunk 增加向量列：embedding 存 JSON 数组字符串（千级切片量级，
--    Java 内存算余弦足够；JSON 可读可调试，优于二进制 BLOB）
ALTER TABLE kb_chunk
    ADD COLUMN embedding LONGTEXT NULL COMMENT '切片向量（JSON 数组），由 embedding 模型生成' AFTER token_estimate,
    ADD COLUMN embedding_model VARCHAR(64) NULL COMMENT '生成向量的模型名，模型更换后需重建索引' AFTER embedding;

-- 2) 多轮会话：会话表
CREATE TABLE IF NOT EXISTS kb_conversation (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    user_id     BIGINT       NOT NULL COMMENT '归属用户',
    title       VARCHAR(200) NOT NULL DEFAULT '新会话' COMMENT '会话标题（取首条问题前若干字）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    PRIMARY KEY (id),
    KEY idx_user_updated (user_id, updated_at)
) ENGINE = InnoDB COMMENT ='知识库多轮会话';

-- 3) 多轮会话：消息表
--    注意与 kb_qa_log 的分工：qa_log 是审计日志（只记问题不记答案，防越权泄漏）；
--    本表是用户自己的对话历史，答案正文本来就是答给该用户本人的，且只允许本人查询/删除。
CREATE TABLE IF NOT EXISTS kb_message (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    conversation_id BIGINT       NOT NULL COMMENT '所属会话',
    role            VARCHAR(16)  NOT NULL COMMENT 'user / assistant',
    content         TEXT         NOT NULL COMMENT '消息正文',
    sources         JSON         NULL COMMENT 'assistant 消息的来源引用快照（JSON 数组）',
    mode            VARCHAR(32)  NULL COMMENT 'retrieval-only / llm-augmented',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_conv_id (conversation_id, id)
) ENGINE = InnoDB COMMENT ='知识库会话消息';
