package com.earlylearning.early_learning_server.identity.service;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.identity.dto.TokenPairResponse;

/**
 * 教师首次注册并激活（契约 registerTeacher）。云端不收密码：教师密码只在平板本地哈希保存。
 */
public interface TeacherRegistrationService {

    /**
     * 用激活码建号并签发第一对凭证。任何一步失败整体回滚，激活码仍为 UNUSED。
     * 成功结果短时重放；缓存丢失后返回 409 {@code SENSITIVE_RESULT_EXPIRED}，不会重复消耗激活码。
     *
     * @throws BusinessException 激活码不存在或不可用 409 {@code LICENSE_UNAVAILABLE}；
     *                           用户名已被占用 409 {@code USERNAME_EXISTS}；触发限流 429
     */
    TokenPairResponse register(String activationCode, String rawUsername, String idempotencyKey);
}
