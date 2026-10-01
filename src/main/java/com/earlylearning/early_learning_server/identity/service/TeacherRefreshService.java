package com.earlylearning.early_learning_server.identity.service;

import com.earlylearning.early_learning_server.identity.dto.TokenPairResponse;

/**
 * 教师刷新凭证（契约 refreshTeacherToken）：用 refresh_token 换一对新的 access / refresh。
 *
 * <ul>
 *   <li>同一幂等键同输入重放完整结果；缓存丢失返回 409 {@code SENSITIVE_RESULT_EXPIRED}。</li>
 *   <li>刚被轮换掉的上一枚 refresh 在 30 秒宽限内仍可使用，返回同一组新凭证；更早的立即失效。</li>
 *   <li>每次都会重新确认账号可用、激活码仍为 ACTIVE。</li>
 * </ul>
 */
public interface TeacherRefreshService {

    /**
     * @throws BusinessException 凭证无效 401 {@code REFRESH_TOKEN_INVALID}；
     *                           账号停用或激活码撤销 403
     */
    TokenPairResponse refresh(String refreshToken, String idempotencyKey);
}
