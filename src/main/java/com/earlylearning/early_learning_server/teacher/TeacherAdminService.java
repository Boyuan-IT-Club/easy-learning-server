package com.earlylearning.early_learning_server.teacher;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.web.PageParams;
import com.earlylearning.early_learning_server.license.LicenseLookup;
import com.earlylearning.early_learning_server.license.LicenseStatus;
import com.earlylearning.early_learning_server.teacher.web.TeacherPageResponse;
import com.earlylearning.early_learning_server.teacher.web.UserAccountResponse;

/**
 * 后台的教师账号管理：列表与启用 / 禁用（契约 listTeachers、updateTeacherStatus）。
 *
 * <p>禁用只改状态，不吊销任何凭证：禁用期间由每个请求的状态校验拦截；
 * 再启用后，未轮换的 refresh 与未过期的 access 继续可用（契约原文）。
 */
@Service
public class TeacherAdminService {

    private final TeacherAccountMapper mapper;
    private final LicenseLookup licenses;

    public TeacherAdminService(TeacherAccountMapper mapper, LicenseLookup licenses) {
        this.mapper = mapper;
        this.licenses = licenses;
    }

    /** @param username 小写后包含匹配，{@code %} 与 {@code _} 按普通字符处理 */
    public TeacherPageResponse list(PageParams page, String username, TeacherStatus status) {
        String pattern = username == null || username.isEmpty()
                ? null : PageParams.containsPattern(username.toLowerCase(Locale.ROOT));
        Integer statusValue = status == null ? null : status.value();
        List<TeacherAccount> items = mapper.selectPage(pattern, statusValue, page.pageSize(), page.offset());
        long total = mapper.countMatching(pattern, statusValue);
        return new TeacherPageResponse(items.stream().map(UserAccountResponse::from).toList(),
                page.page(), page.pageSize(), total);
    }

    /**
     * @throws BusinessException 404；启用时绑定激活码不是 ACTIVE → 409 LICENSE_REVOKED
     */
    @Transactional
    public UserAccountResponse updateStatus(int id, TeacherStatus target) {
        TeacherAccount account = mapper.selectForUpdate(id);
        if (account == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (account.getStatus() == target) {
            return UserAccountResponse.from(account);
        }
        if (target == TeacherStatus.ENABLED) {
            Optional<LicenseStatus> license = licenses.statusOfUser(id);
            if (license.isEmpty() || license.get() != LicenseStatus.ACTIVE) {
                // LICENSE_REVOKED 默认是 403（鉴权时）；这里是"状态冲突"，契约规定 409
                throw new BusinessException(ErrorCode.LICENSE_REVOKED, HttpStatus.CONFLICT, null, null, null);
            }
        }
        mapper.updateStatus(id, target.value());
        account.setStatus(target);
        return UserAccountResponse.from(account);
    }
}
