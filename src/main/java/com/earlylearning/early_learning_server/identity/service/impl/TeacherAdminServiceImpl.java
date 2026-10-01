package com.earlylearning.early_learning_server.identity.service.impl;

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
import com.earlylearning.early_learning_server.identity.service.TeacherAdminService;

/** {@link TeacherAdminService} 的实现。 */
@Service
public class TeacherAdminServiceImpl implements TeacherAdminService {

    private final TeacherAccountMapper teachers;
    private final LicenseMapper licenses;

    public TeacherAdminServiceImpl(TeacherAccountMapper teachers, LicenseMapper licenses) {
        this.teachers = teachers;
        this.licenses = licenses;
    }

    @Override
    public PageResponse<UserAccountResponse> list(PageQuery page, String username, TeacherStatus status) {
        String pattern = username == null || username.isEmpty()
                ? null : PageQuery.containsPattern(username.toLowerCase(Locale.ROOT));
        Integer statusValue = status == null ? null : status.value();
        return PageResponse.of(page, teachers.selectPage(pattern, statusValue, page.pageSize(), page.offset()),
                teachers.countMatching(pattern, statusValue), UserAccountResponse::from);
    }

    @Override
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
