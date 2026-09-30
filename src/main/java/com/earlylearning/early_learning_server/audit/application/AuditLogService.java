package com.earlylearning.early_learning_server.audit.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.earlylearning.early_learning_server.audit.domain.AuditEntry;
import com.earlylearning.early_learning_server.audit.domain.AuditLog;
import com.earlylearning.early_learning_server.audit.domain.AuditPage;
import com.earlylearning.early_learning_server.audit.domain.AuditQuery;
import com.earlylearning.early_learning_server.audit.infrastructure.AuditLogMapper;
import com.earlylearning.early_learning_server.common.logging.RequestOrigin;

import tools.jackson.databind.ObjectMapper;

/**
 * 审计的写入与查询。
 *
 * <ul>
 *   <li>{@link #record}：加入调用方事务——业务成功审计必在，业务回滚审计一起消失；</li>
 *   <li>{@link #recordIndependently}：独立事务——用于"失败本身要留痕"的场景（登录失败、恢复码错误），
 *       调用方随后抛异常回滚时，这条记录不受影响。</li>
 * </ul>
 */
@Service
public class AuditLogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogMapper mapper;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditEntry entry) {
        mapper.insert(toLog(entry));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(AuditEntry entry) {
        mapper.insert(toLog(entry));
    }

    public AuditPage query(AuditQuery query) {
        int size = Math.clamp(query.size(), 1, MAX_PAGE_SIZE);
        int page = Math.max(query.page(), 1);
        String action = query.action() == null ? null : query.action().name();
        String targetType = query.targetType() == null ? null : query.targetType().name();
        List<AuditLog> items = mapper.selectPage(action, targetType, query.targetId(),
                query.from(), query.to(), size, (long) (page - 1) * size);
        long total = mapper.countMatching(action, targetType, query.targetId(), query.from(), query.to());
        return new AuditPage(items, total, page, size);
    }

    private AuditLog toLog(AuditEntry entry) {
        AuditLog log = new AuditLog();
        log.setActorType(entry.actorType());
        log.setActorId(entry.actorId());
        log.setAction(entry.action());
        log.setTargetType(entry.targetType());
        log.setTargetId(entry.targetId());
        log.setReason(entry.reason());
        log.setDetail(entry.detail() == null || entry.detail().isEmpty()
                ? null : objectMapper.writeValueAsString(entry.detail()));
        log.setResult(entry.success() ? "SUCCESS" : "FAILED");
        log.setIp(RequestOrigin.clientIp());
        log.setTraceId(RequestOrigin.traceId());
        return log;
    }
}
