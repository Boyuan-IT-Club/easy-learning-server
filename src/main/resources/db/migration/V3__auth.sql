-- 01 账号与鉴权：设备绑定、恢复码、激活码元数据、管控审计。
-- 只新增列、索引和表，不修改 V1 已有列的定义。

ALTER TABLE user_account
    ADD COLUMN device_id CHAR(36) NULL COMMENT '当前绑定设备 UUID；解绑后为空',
    ADD COLUMN device_bound_at TIMESTAMP NULL DEFAULT NULL COMMENT '最近一次绑定时间',
    ADD COLUMN last_refresh_at TIMESTAMP NULL DEFAULT NULL COMMENT '最近一次成功刷新',
    ADD COLUMN recovery_code_hash VARCHAR(64) NULL COMMENT '恢复码 HMAC；未签发或已使用为空',
    ADD COLUMN recovery_code_expires_at TIMESTAMP NULL DEFAULT NULL COMMENT '恢复码到期时间',
    ADD COLUMN recovery_code_failed_count INT NOT NULL DEFAULT 0 COMMENT '恢复码连续错误次数，达到上限即作废',
    ADD COLUMN disabled_reason VARCHAR(200) NULL COMMENT '最近一次停用原因',
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    ADD CONSTRAINT uk_user_account_device_id UNIQUE (device_id),
    ADD CONSTRAINT uk_user_account_refresh_token_hash UNIQUE (refresh_token_hash);

ALTER TABLE user_license
    ADD COLUMN code_hint CHAR(4) NULL COMMENT '激活码末 4 位，仅用于人工核对，不能用于激活',
    ADD COLUMN remark VARCHAR(100) NULL COMMENT '发放备注',
    ADD COLUMN created_by INT NULL COMMENT '生成该码的管理员',
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    ADD COLUMN revoked_at TIMESTAMP NULL DEFAULT NULL COMMENT '撤销时间',
    ADD COLUMN revoked_by INT NULL COMMENT '撤销的管理员',
    ADD COLUMN revoke_reason VARCHAR(200) NULL COMMENT '撤销原因',
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    ADD CONSTRAINT uk_user_license_user_id UNIQUE (user_id),
    ADD KEY idx_user_license_status_created (status, created_at),
    ADD CONSTRAINT fk_user_license_created_by FOREIGN KEY (created_by) REFERENCES admin_account (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_user_license_revoked_by FOREIGN KEY (revoked_by) REFERENCES admin_account (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审计主键',
    actor_type VARCHAR(16) NOT NULL COMMENT 'ADMIN / TEACHER / SYSTEM',
    actor_id INT NULL COMMENT '操作者 id；SYSTEM 或未识别身份时为空',
    action VARCHAR(64) NOT NULL COMMENT '操作代码',
    target_type VARCHAR(32) NULL COMMENT 'ADMIN / TEACHER / LICENSE',
    target_id VARCHAR(64) NULL COMMENT '目标 id；批量操作记首个 id，其余写入 detail',
    reason VARCHAR(200) NULL COMMENT '操作原因',
    detail VARCHAR(1000) NULL COMMENT '非敏感补充信息（JSON），不得包含码、Token、密码',
    result VARCHAR(16) NOT NULL COMMENT 'SUCCESS / FAILED',
    ip VARCHAR(45) NULL COMMENT '来源 IP',
    trace_id VARCHAR(64) NULL COMMENT '与日志关联',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_audit_log_created (created_at),
    KEY idx_audit_log_target (target_type, target_id),
    CONSTRAINT ck_audit_log_actor_type CHECK (actor_type IN ('ADMIN', 'TEACHER', 'SYSTEM')),
    CONSTRAINT ck_audit_log_result CHECK (result IN ('SUCCESS', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '管控审计，只记录元数据，不记录业务正文';
