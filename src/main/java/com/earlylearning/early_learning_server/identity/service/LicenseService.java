package com.earlylearning.early_learning_server.identity.service;

import com.earlylearning.early_learning_server.common.enums.LicenseStatus;
import com.earlylearning.early_learning_server.common.paging.PageQuery;
import com.earlylearning.early_learning_server.common.web.PageResponse;
import com.earlylearning.early_learning_server.identity.dto.CreateLicensesResponse;
import com.earlylearning.early_learning_server.identity.dto.LicenseResponse;

/** 激活码管理（契约 createLicenses、listLicenses、revokeLicense）。 */
public interface LicenseService {

    /**
     * 单个或批量生成激活码，整批一个事务。
     *
     * <p>激活码原文只出现在首次结果与短时重放里；重放缓存过期返回 409 {@code SENSITIVE_RESULT_EXPIRED}，
     * {@code details.license_ids} 给出这次实际创建的全部 id，不会重新生成。
     *
     * @param count          1 ~ 100
     * @param adminId        操作的管理员
     * @param idempotencyKey 幂等键
     */
    CreateLicensesResponse create(Integer count, int adminId, String idempotencyKey);

    /**
     * 分页列出激活码（不含原文）。
     *
     * @param status 可选，按状态筛选
     * @param userId 可选，按绑定的教师筛选
     */
    PageResponse<LicenseResponse> list(PageQuery page, LicenseStatus status, Integer userId);

    /**
     * 撤销激活码：UNUSED / ACTIVE 均可撤销，已 REVOKED 的直接返回当前状态。
     * 撤销已激活的码会在同一事务里停用绑定的教师；不可恢复，不影响平板本地的离线业务。
     *
     * @throws BusinessException 激活码不存在 404
     */
    LicenseResponse revoke(int id);
}
