package com.earlylearning.early_learning_server.identity.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.paging.PageQuery;
import com.earlylearning.early_learning_server.common.web.PageResponse;
import com.earlylearning.early_learning_server.entity.TeacherAccount;
import com.earlylearning.early_learning_server.entity.TeacherStatus;
import com.earlylearning.early_learning_server.identity.dto.UserAccountResponse;
import com.earlylearning.early_learning_server.identity.mapper.LicenseMapper;
import com.earlylearning.early_learning_server.identity.mapper.TeacherAccountMapper;

/**
 * 管理端的教师账号管理：列表与启用 / 停用（契约 listTeachers、updateTeacherStatus）。
 *
 * <p>停用只改状态，不吊销任何凭证：停用期间由每个请求的状态校验拦截；
 * 再启用后，未轮换的 refresh 与未过期的 access 继续可用（契约原文）。
 */
@Service
public class TeacherAdminService {

    private final TeacherAccountMapper teachers;
    private final LicenseMapper licenses;

    public TeacherAdminService(TeacherAccountMapper teachers, LicenseMapper licenses) {
        this.teachers = teachers;
        this.licenses = licenses;
    }

    /** @param username 小写后包含匹配，{@code %} 与 {@code _} 按普通字符处理 */
    public PageResponse<UserAccountResponse> list(PageQuery page, String username, TeacherStatus status) {
        String pattern = username == null || username.isEmpty()
                ? null : PageQuery.containsPattern(username.toLowerCase(Locale.ROOT));
        Integer statusValue = status == null ? null : status.value();
        return PageResponse.of(page, teachers.selectPage(pattern, statusValue, page.pageSize(), page.offset()),
                teachers.countMatching(pattern, statusValue), UserAccountResponse::from);
    }

    /** @throws BusinessException 404；启用时绑定激活码不是 ACTIVE → 409 LICENSE_REVOKED */
    @Transactional
    public UserAccountResponse updateStatus(int id, TeacherStatus target) {
        TeacherAccount account = teachers.selectForUpdate(id);
        if (account == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (account.getStatus() == target) {
            return UserAccountResponse.from(account);
        }
        if (target == TeacherStatus.ENABLED) {
            account.ensureCanEnable(licenses.selectByUserId(id));
        }
        teachers.updateStatus(id, target.value());
        account.setStatus(target);
        return UserAccountResponse.from(account);
    }
}
