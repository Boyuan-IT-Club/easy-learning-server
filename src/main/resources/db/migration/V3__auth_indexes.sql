-- 账号与鉴权：只加两个唯一约束，不加字段、不加表（契约："无需新增表、字段"）。

-- 刷新按哈希查账号，需要索引；唯一约束同时防止两个账号共用一个哈希。
-- 尚未写入哈希的行为 NULL，MySQL 唯一索引允许多个 NULL。
ALTER TABLE user_account
    ADD CONSTRAINT uk_user_account_refresh_token_hash UNIQUE (refresh_token_hash);

-- 一个教师账号只绑定一个激活码。唯一索引建好后外键改用它，V1 的普通索引随之多余。
ALTER TABLE user_license
    ADD CONSTRAINT uk_user_license_user_id UNIQUE (user_id),
    DROP INDEX idx_user_license_user_id;
