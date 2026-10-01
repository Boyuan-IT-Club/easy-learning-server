package com.earlylearning.early_learning_server.teacher.application;

import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.license.domain.LicenseRevoked;
import com.earlylearning.early_learning_server.teacher.domain.TeacherAccount;
import com.earlylearning.early_learning_server.teacher.domain.TeacherStatus;
import com.earlylearning.early_learning_server.teacher.infrastructure.TeacherAccountMapper;

/**
 * 教师账号的基础操作，供 auth 模块的注册、刷新与鉴权调用，也处理激活码撤销事件。
 *
 * <p>写操作要求调用方已开启事务（{@code MANDATORY}）：注册是"建号 + 占码 + 写 refresh 哈希"一个事务，
 * 撤销激活码是"撤码 + 禁用教师"一个事务（契约）。
 */
@Service
public class TeacherAccountService {

    private final TeacherAccountMapper mapper;

    public TeacherAccountService(TeacherAccountMapper mapper) {
        this.mapper = mapper;
    }

    public TeacherAccount find(int id) {
        return mapper.selectById(id);
    }

    /** @param username 已规范化为小写 */
    @Transactional(propagation = Propagation.MANDATORY)
    public TeacherAccount create(String username) {
        if (mapper.selectByUsername(username) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        TeacherAccount account = new TeacherAccount();
        account.setUsername(username);
        account.setStatus(TeacherStatus.ENABLED);
        try {
            mapper.insert(account);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        // 回读一次，拿到数据库生成的 created_at
        return mapper.selectById(account.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveRefreshHash(int id, String hash) {
        mapper.updateRefreshHash(id, hash);
    }

    /** 按当前 refresh 哈希锁定账号：同一账号的并发刷新在这里串行。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public TeacherAccount lockByRefreshHash(String hash) {
        return mapper.selectByRefreshHashForUpdate(hash);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TeacherAccount lockById(int id) {
        return mapper.selectForUpdate(id);
    }

    /** @return 轮换成功；旧哈希已不是当前值时为 false */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean rotateRefresh(int id, String oldHash, String newHash) {
        return mapper.rotateRefresh(id, oldHash, newHash) == 1;
    }

    /**
     * 撤销已激活的码时，与撤码同一事务禁用绑定教师（契约 revokeLicense）。
     *
     * <p>用同步的 {@code @EventListener}：它在发布方（撤销）的事务里执行，失败会让撤销一起回滚。
     * 不能换成 {@code @TransactionalEventListener} 或 Modulith 的 {@code @ApplicationModuleListener}——
     * 那两个都在提交之后执行，撤码与禁用就不再是同一事务。
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onLicenseRevoked(LicenseRevoked event) {
        mapper.updateStatus(event.userId(), TeacherStatus.DISABLED.value());
    }
}
