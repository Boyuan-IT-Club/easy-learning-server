-- 本地开发/调试用的种子数据。**这不是 Flyway 迁移。**
--
-- 位置 db/seed/ —— Flyway 只扫 db/migration/（见 application-dev.yml 的 locations），
-- 所以本文件不会被自动执行，也不进 flyway_schema_history；生产环境永远不会用到它。
--
-- 幂等：末尾是空更新的 ON DUPLICATE KEY UPDATE，重复执行只跳过已存在的行。
--
-- ⚠️ 这些 object_key 在 OSS 里没有对应真实对象，因此：
--    · 元数据类接口（列表、查询、按状态过滤）可直接用这些行调试；
--    · 签发下载地址能成功生成，但真去 GET 会 404 —— 签名本身不检查对象是否存在。
--    · sha256 是这些示例标签的真实摘要，形状合法（^[a-f0-9]{64}$），内容不对应任何真实文件。
--
-- 只种 storage_cloud_file：其余表（grammar/course/dict_* 等）属于其他模块的数据边界，
-- 数据应由各自模块产生；测试需要引用关系时在测试里临时插入。
--
-- 执行（在 easy-learning-server 目录）：
--   docker exec -i early-learning-mysql mysql -uroot -p"$DB_PASSWORD" early_learning < src/main/resources/db/seed/dev_seed.sql

INSERT INTO storage_cloud_file
    (file_code, object_key, file_kind, file_name, mime_type, size_bytes, duration_ms, status, sha256, created_at)
VALUES

    ('CF_DEMO_IMG_0001', 'demo-seed/image/CF_DEMO_IMG_0001.png', 'IMAGE', '示例图片-植物.png', 'image/png', 245760, NULL, 'READY', 'd7bdd545f09d8a73c2b990337c8211d708a04ccd9748627685e4fc79cc038039', '2026-09-20 09:12:00'),
    ('CF_DEMO_IMG_0002', 'demo-seed/image/CF_DEMO_IMG_0002.jpg', 'IMAGE', '示例图片-课堂.jpg', 'image/jpeg', 1048576, NULL, 'READY', '6987740fb624e3e9943ec5d9ac5519b72cea1b35fb4bde5719df3923a36c08f7', '2026-09-21 10:30:00'),
    ('CF_DEMO_IMG_0003', 'demo-seed/image/CF_DEMO_IMG_0003.png', 'IMAGE', '示例图片-损坏.png', 'image/png', 12288, NULL, 'INVALID', NULL, '2026-09-22 11:05:00'),
    ('CF_DEMO_IMG_0004', 'demo-seed/image/CF_DEMO_IMG_0004.webp', 'IMAGE', '示例图片-教具.webp', 'image/webp', 66560, NULL, 'READY', 'a87b9502a134b0a1a1c7d26ad03390702896403121ec7035ca923221990a990b', '2026-09-23 15:40:00'),
    ('CF_DEMO_PDF_0001', 'demo-seed/pdf/CF_DEMO_PDF_0001.pdf', 'PDF', '示例手册-教师用书.pdf', 'application/pdf', 5242880, NULL, 'READY', 'fafcc7372d9cf30b40698387175c0e243909152431231ebe0a7d57f284b04bbd', '2026-09-24 08:20:00'),
    ('CF_DEMO_PDF_0002', 'demo-seed/pdf/CF_DEMO_PDF_0002.pdf', 'PDF', '示例手册-已删除.pdf', 'application/pdf', 88000, NULL, 'DELETED', NULL, '2026-09-24 16:55:00'),
    ('CF_DEMO_AUDIO_0001', 'demo-seed/audio/CF_DEMO_AUDIO_0001.m4a', 'AUDIO', '示例录音-故事.m4a', 'audio/mp4', 3145728, 62000, 'READY', '1d05d5db18261819bbc717767603ddd07715aeb2c5eec0d36a29d9400c0b69de', '2026-09-25 09:00:00'),
    ('CF_DEMO_AUDIO_0002', 'demo-seed/audio/CF_DEMO_AUDIO_0002.mp3', 'AUDIO', '示例录音-上传中.mp3', 'audio/mpeg', 0, NULL, 'UPLOADING', NULL, '2026-09-25 09:05:00')
ON DUPLICATE KEY UPDATE file_code = file_code;
