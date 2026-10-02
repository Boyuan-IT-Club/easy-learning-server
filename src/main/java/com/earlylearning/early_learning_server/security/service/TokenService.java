package com.earlylearning.early_learning_server.security.service;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.security.model.IssuedToken;
import com.earlylearning.early_learning_server.security.model.TeacherTokens;

/**
 * Token 的签发、校验与吊销。只回答"这枚 Token 是不是我们发的、过没过期、属于谁"；
 * 账号能不能用由各 {@code BearerAuthenticator} 查库判断。
 *
 * <p>Redis 写入一律推迟到事务提交后：注册回滚了，就不能留下一枚可用的 Token。
 */
public interface TokenService {

    TeacherTokens issueTeacher(int userId);

    IssuedToken issueAdmin(int adminId);

    /** @throws BusinessException TOKEN_INVALID / TOKEN_EXPIRED / DEPENDENCY_UNAVAILABLE */
    int requireTeacher(String token);

    /** @throws BusinessException TOKEN_INVALID / TOKEN_EXPIRED / DEPENDENCY_UNAVAILABLE */
    int requireAdmin(String token);

    /** 改密码或停用后，该管理员已签发的 Token 全部失效（契约 updateAdminAccount）。提交后执行。 */
    void revokeAdminAfterCommit(int adminId);
}
