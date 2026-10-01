package com.earlylearning.early_learning_server.identity.service;

import com.earlylearning.early_learning_server.common.paging.PageQuery;
import com.earlylearning.early_learning_server.common.web.PageResponse;
import com.earlylearning.early_learning_server.entity.AdminStatus;
import com.earlylearning.early_learning_server.identity.dto.AdminAccountResponse;

/**
 * 管理员账号维护（契约 createAdminAccount、listAdminAccounts、updateAdminAccount）。
 * 所有管理员同权限，无 RBAC。
 */
public interface AdminAccountService {

    /**
     * 创建管理员，默认 ACTIVE。仅已登录的管理员可调用。
     *
     * @param rawUsername    用户名原文，按统一规则校验并转小写
     * @param password       明文密码，须满足管理员密码规则
     * @param idempotencyKey 幂等键：同键同输入重放首次结果，同键不同输入 409
     * @return 新建的账号
     */
    AdminAccountResponse create(String rawUsername, String password, String idempotencyKey);

    /** 部署时初始化首个管理员；不走幂等，只由 {@link AdminBootstrap} 在启动时调用。 */
    AdminAccountResponse bootstrap(String rawUsername, String password);

    /** 是否已经存在任何管理员。 */
    boolean anyExists();

    /**
     * 分页列出管理员。
     *
     * @param rawUsername 可选，转小写后精确匹配
     * @param status      可选，按状态筛选
     */
    PageResponse<AdminAccountResponse> list(PageQuery page, String rawUsername, AdminStatus status);

    /**
     * 修改密码和 / 或状态：至少传一项，未传的保持原值；重复提交相同目标状态不产生额外变化。
     * 改密码或停用后，该管理员已签发的全部 Token 失效。
     *
     * @param password  可选，新密码
     * @param rawStatus 可选，{@code ACTIVE} / {@code DISABLED}
     * @throws BusinessException 账号不存在 404；两项都没传 400
     */
    AdminAccountResponse update(int id, String password, String rawStatus);
}
