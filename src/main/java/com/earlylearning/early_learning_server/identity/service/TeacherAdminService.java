package com.earlylearning.early_learning_server.identity.service;

import com.earlylearning.early_learning_server.common.enums.TeacherStatus;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.paging.PageQuery;
import com.earlylearning.early_learning_server.common.web.PageResponse;
import com.earlylearning.early_learning_server.identity.dto.UserAccountResponse;

/**
 * 管理端的教师账号管理：列表与启用 / 停用（契约 listTeachers、updateTeacherStatus）。
 *
 * <p>停用只改状态，不吊销任何凭证：停用期间由每个请求的状态校验拦截；
 * 再启用后，未轮换的 refresh 与未过期的 access 继续可用（契约原文）。
 */
public interface TeacherAdminService {

    /**
     * 分页列出教师。
     *
     * @param username 可选，转小写后包含匹配，{@code %} 与 {@code _} 按普通字符处理
     * @param status   可选，按状态筛选
     */
    PageResponse<UserAccountResponse> list(PageQuery page, String username, TeacherStatus status);

    /**
     * 启用或停用教师；目标状态与当前相同时直接返回。
     *
     * @throws BusinessException 教师不存在 404；启用时绑定的激活码不是 ACTIVE → 409 {@code LICENSE_REVOKED}
     */
    UserAccountResponse updateStatus(int id, TeacherStatus target);
}
