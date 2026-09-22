-- 云端首版 Schema；依据 docs/技术方案.md 及飞书模块功能细则。
-- 已确认的物理映射：INT 自增主键；Code/内容版本/用户名 VARCHAR(64)；
-- 名称 VARCHAR(255)；哈希 VARCHAR(255)；释义 TEXT；云端时间统一 TIMESTAMP。
-- 用户名、稳定 Code、版本和对象路径使用 utf8mb4_0900_bin 精确比较。
-- 仅创建云端业务表；不包含客户端 SQLite 表、AI 任务表或初始账号。

CREATE TABLE admin_account (
    id INT NOT NULL AUTO_INCREMENT COMMENT '管理员主键',
    username VARCHAR(64) NOT NULL COMMENT '用户名，区分大小写',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    status VARCHAR(16) NOT NULL COMMENT 'ACTIVE / DISABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_account_username UNIQUE (username),
    CONSTRAINT ck_admin_account_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '管理员账号';

CREATE TABLE user_account (
    id INT NOT NULL AUTO_INCREMENT COMMENT '教师云端账号主键',
    username VARCHAR(64) NOT NULL COMMENT '用户名，区分大小写',
    refresh_token_hash VARCHAR(255) NULL COMMENT '刷新 Token 哈希，不保存明文 Token',
    status INT NOT NULL COMMENT '1=启用，0=禁用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_user_account_username UNIQUE (username),
    CONSTRAINT ck_user_account_status CHECK (status IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '教师云端账号，不保存教师密码';

CREATE TABLE user_license (
    id INT NOT NULL AUTO_INCREMENT COMMENT '激活码主键',
    user_id INT NULL COMMENT '绑定的教师账号，未使用时为空',
    activation_code_hash VARCHAR(255) NOT NULL COMMENT '激活码哈希',
    status VARCHAR(16) NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED / ACTIVE / REVOKED',
    activated_at TIMESTAMP NULL DEFAULT NULL COMMENT '实际激活时写入，生成激活码时为空',
    PRIMARY KEY (id),
    CONSTRAINT uk_user_license_activation_code_hash UNIQUE (activation_code_hash),
    KEY idx_user_license_user_id (user_id),
    CONSTRAINT fk_user_license_user FOREIGN KEY (user_id) REFERENCES user_account (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_user_license_status CHECK (status IN ('UNUSED', 'ACTIVE', 'REVOKED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '教师激活码及账号绑定';

CREATE TABLE storage_cloud_file (
    id INT NOT NULL AUTO_INCREMENT COMMENT '云端文件主键',
    file_code VARCHAR(64) NOT NULL COMMENT '跨端稳定文件编号',
    object_key VARCHAR(512) NOT NULL COMMENT '对象存储路径',
    file_kind VARCHAR(16) NOT NULL COMMENT 'AUDIO / PDF / IMAGE',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    mime_type VARCHAR(127) NOT NULL COMMENT '实际文件 MIME 类型',
    size_bytes INT NOT NULL COMMENT '文件字节数',
    duration_ms INT NULL COMMENT '音频时长，非音频为空',
    status VARCHAR(16) NOT NULL COMMENT 'UPLOADING / READY / INVALID / DELETED',
    sha256 VARCHAR(64) NULL COMMENT 'SHA256 校验值，上传完成后由存储服务校验',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_storage_cloud_file_code UNIQUE (file_code),
    CONSTRAINT uk_storage_cloud_file_object_key UNIQUE (object_key),
    CONSTRAINT ck_storage_cloud_file_kind CHECK (file_kind IN ('AUDIO', 'PDF', 'IMAGE')),
    CONSTRAINT ck_storage_cloud_file_status CHECK (status IN ('UPLOADING', 'READY', 'INVALID', 'DELETED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '云端官方资源文件元数据';

CREATE TABLE grammar (
    id INT NOT NULL AUTO_INCREMENT COMMENT '语法要素主键',
    grammar_code VARCHAR(64) NOT NULL COMMENT '跨端稳定语法编号',
    name VARCHAR(255) NOT NULL COMMENT '显示名称',
    version INT NOT NULL COMMENT '当前版本，修改内容时递增',
    icon_file_id INT NULL COMMENT '语法图标文件',
    status VARCHAR(16) NOT NULL COMMENT 'ACTIVE / DISABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_grammar_code UNIQUE (grammar_code),
    KEY idx_grammar_icon_file_id (icon_file_id),
    CONSTRAINT fk_grammar_icon_file FOREIGN KEY (icon_file_id) REFERENCES storage_cloud_file (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_grammar_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '语法要素，仅保留当前版本';

CREATE TABLE course (
    id INT NOT NULL AUTO_INCREMENT COMMENT '官方课程版本主键',
    official_course_code VARCHAR(64) NOT NULL COMMENT '官方课程稳定编号',
    content_version VARCHAR(64) NOT NULL COMMENT '课程内容版本',
    name VARCHAR(255) NOT NULL COMMENT '课程名称',
    activity_configs_json JSON NOT NULL COMMENT '既定 ActivityConfig 配置，业务结构由课程模块校验',
    status VARCHAR(16) NOT NULL COMMENT 'ACTIVE / DISABLED / DELETED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_course_code_version UNIQUE (official_course_code, content_version),
    CONSTRAINT ck_course_status CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '官方课程内容版本';

CREATE TABLE assessment_material (
    id INT NOT NULL AUTO_INCREMENT COMMENT '评估材料版本主键',
    official_material_code VARCHAR(64) NOT NULL COMMENT '官方评估材料稳定编号',
    content_version VARCHAR(64) NOT NULL COMMENT '材料内容版本',
    name VARCHAR(255) NOT NULL COMMENT '材料名称',
    activity_configs_json JSON NOT NULL COMMENT '既定 ActivityConfig 配置，业务结构由评估模块校验',
    status VARCHAR(16) NOT NULL COMMENT 'ACTIVE / DISABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_assessment_material_code_version UNIQUE (official_material_code, content_version),
    CONSTRAINT ck_assessment_material_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '官方前测及复评材料内容版本';

CREATE TABLE dict_entry (
    id INT NOT NULL AUTO_INCREMENT COMMENT '字典条目主键',
    entry_code VARCHAR(64) NOT NULL COMMENT '跨端稳定字典条目编号',
    term VARCHAR(255) NOT NULL COMMENT '中文短语',
    pinyin VARCHAR(255) NOT NULL COMMENT '拼音',
    definition TEXT NOT NULL COMMENT '词语释义',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_dict_entry_code UNIQUE (entry_code)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '字典条目，不保留条目历史版本';

CREATE TABLE dict_gloss (
    id INT NOT NULL AUTO_INCREMENT COMMENT '课程词语位置主键',
    course_id INT NOT NULL COMMENT '对应的具体课程版本',
    entry_id INT NOT NULL COMMENT '字典条目',
    start_offset INT NOT NULL COMMENT '释义词起始位置',
    end_offset INT NOT NULL COMMENT '释义词终止位置',
    PRIMARY KEY (id),
    KEY idx_dict_gloss_course_id (course_id),
    KEY idx_dict_gloss_entry_id (entry_id),
    CONSTRAINT fk_dict_gloss_course FOREIGN KEY (course_id) REFERENCES course (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_dict_gloss_entry FOREIGN KEY (entry_id) REFERENCES dict_entry (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '课程版本中的词语释义位置';
