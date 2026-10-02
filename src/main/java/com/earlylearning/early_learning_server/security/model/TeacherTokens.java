package com.earlylearning.early_learning_server.security.model;

/** 刚签发的一对教师凭证。refresh 哈希由调用方写入 user_account（契约：数据库仅保存当前 refresh_token_hash）。 */
public record TeacherTokens(IssuedToken access, IssuedToken refresh) {
}
