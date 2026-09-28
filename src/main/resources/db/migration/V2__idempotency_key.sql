-- M1 幂等机制。依据契约 info.description（同一操作重试沿用原键；同键不同输入返回 409 IDEMPOTENCY_CONFLICT）
-- 与各写入接口的 Idempotency-Key / request_id 约定。
--
-- 键在业务事务内占用并回填，因此不存在对外的「占用中」中间态：
-- 已提交的行必然带响应快照；业务失败回滚时占用行一并消失，该键可以重试。

CREATE TABLE idempotency_record (
    id INT NOT NULL AUTO_INCREMENT COMMENT '幂等记录主键',
    scope VARCHAR(64) NOT NULL COMMENT '幂等域，决定哪些请求共享同一键空间',
    idempotency_key VARCHAR(128) NOT NULL COMMENT 'Idempotency-Key 头或 AI 接口的 request_id',
    request_hash VARCHAR(64) NOT NULL COMMENT '输入指纹，用于判定同键是否同输入',
    http_status INT NULL COMMENT '首次响应的 HTTP 状态，占用后由业务回填',
    response_body TEXT NULL COMMENT '首次响应的响应体快照，重试时原样返回',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次占用时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_record_scope_key UNIQUE (scope, idempotency_key)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '幂等键占用与响应快照';
