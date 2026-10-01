package com.earlylearning.early_learning_server.identity.service.impl;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.paging.PageQuery;
import com.earlylearning.early_learning_server.common.web.PageResponse;
import com.earlylearning.early_learning_server.entity.TeacherAccount;
import com.earlylearning.early_learning_server.enums.TeacherStatus;
import com.earlylearning.early_learning_server.identity.dto.UserAccountResponse;
import com.earlylearning.early_learning_server.identity.mapper.LicenseMapper;
import com.earlylearning.early_learning_server.identity.mapper.TeacherAccountMapper;
import com.earlylearning.early_learning_server.identity.service.TeacherAdminService;

/** {@link TeacherAdminService} 的实现。 */
@Service
public class TeacherAdminServiceImpl implements TeacherAdminService {

    private static final Logger log = LoggerFactory.getLogger(TeacherAdminServiceImpl.class);

    private final TeacherAccountMapper teacherAccountMapper;
    private final LicenseMapper licenseMapper;

    public TeacherAdminServiceImpl(TeacherAccountMapper teacherAccountMapper, LicenseMapper licenseMapper) {
        this.teacherAccountMapper = teacherAccountMapper;
        this.licenseMapper = licenseMapper;
    }

    @Override
    public PageResponse<UserAccountResponse> list(PageQuery page, String username, TeacherStatus status) {
        String pattern = username == null || username.isEmpty()
                ? null : PageQuery.containsPattern(username.toLowerCase(Locale.ROOT));
        Integer statusValue = status == null ? null : status.value();
        List<TeacherAccount> rows = teacherAccountMapper.selectPage(pattern, statusValue, page.pageSize(), page.offset());
        return PageResponse.of(page, rows, teacherAccountMapper.countMatching(pattern, statusValue),
                UserAccountResponse::from);
    }

    @Override
    @Transactional
    public UserAccountResponse updateStatus(int id, TeacherStatus target) {
        TeacherAccount account = teacherAccountMapper.selectForUpdate(id);
        if (account == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (account.getStatus() == target) {
            return UserAccountResponse.from(account);
        }
        if (target == TeacherStatus.ENABLED) {
            account.ensureCanEnable(licenseMapper.selectByUserId(id));
        }
        teacherAccountMapper.updateStatus(id, target.value());
        log.info("教师状态变更 userId={} from={} to={}", id, account.getStatus(), target);
        account.setStatus(target);
        return UserAccountResponse.from(account);
    }
}
