package com.earlylearning.early_learning_server.common.idempotency;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import java.util.Optional;

import com.earlylearning.early_learning_server.common.error.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 幂等键的占用与重放。
 *
 * <pre>
 *   Optional&lt;StoredResponse&gt; replayed = idempotency.claim(scope, key, fingerprint);
 *   if (replayed.isPresent()) { return replayed.get(); }   // 重试：原样返回首次结果
 *   … 执行业务写入 …
 *   idempotency.record(scope, key, 201, domainSnapshot);   // 快照是任意可序列化对象，不必是 HTTP 响应
 * </pre>
 *
 * <p>两个方法都要求调用方已开启事务（{@code MANDATORY}），占用与回填才会一起提交或一起回滚。
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordMapper mapper;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 占用幂等键。
     *
     * @return 该键已用于同一输入 → 首次响应，调用方直接原样返回；键空闲 → {@code empty}，调用方继续执行
     * @throws BusinessException 该键已存在但输入不同（409 {@code IDEMPOTENCY_CONFLICT}）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<StoredResponse> claim(IdempotencyScope scope, String key, String inputFingerprint) {
        IdempotencyRecord existing = mapper.find(scope.value(), key);
        if (existing != null) {
            return Optional.of(replay(scope, key, existing, inputFingerprint));
        }

        try {
            mapper.insert(IdempotencyRecord.claimed(scope.value(), key, inputFingerprint));
        } catch (DuplicateKeyException e) {
            // 并发同键：对方先占了位。它的占用与响应同事务提交，所以等它提交后按当前读取回胜者。
            IdempotencyRecord winner = mapper.findForUpdate(scope.value(), key);
            if (winner == null) {
                throw e;
            }
            return Optional.of(replay(scope, key, winner, inputFingerprint));
        }
        return Optional.empty();
    }

    /**
     * 只读探测：键是否已被占用，且是否同一输入。
     *
     * <p>存在的意义是省掉一次无谓的重活：上传重试时，若已能判定这是重放，就不必把 500MB 再传一遍。
     * 它不参与正确性——真正的判定仍在 {@link #claim} 里，那个是原子的、在事务内的。
     *
     * @return 与 {@link #claim} 同语义：命中返回首次响应，未占用返回 {@code empty}
     * @throws BusinessException 该键已存在但输入不同
     */
    public Optional<StoredResponse> peek(IdempotencyScope scope, String key, String inputFingerprint) {
        IdempotencyRecord existing = mapper.find(scope.value(), key);
        return existing == null
                ? Optional.empty()
                : Optional.of(replay(scope, key, existing, inputFingerprint));
    }

    /**
     * 回填首次结果的快照。
     *
     * <p>快照是调用方领域里的任意可序列化对象：幂等只负责"同一键同输入得到同一结果"，
     * 不关心结果长什么样；把它变回 HTTP 响应是调用方 web 层的事，重放时按同样的方式映射即可。
     *
     * @return 序列化后的快照 JSON，调用方需要时可用它还原结果
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String record(IdempotencyScope scope, String key, int httpStatus, Object snapshot) {
        // Jackson 3 的 JacksonException 是非受检异常：快照不可序列化属于编程错误，直接上抛由兜底处理器记堆栈。
        String body = objectMapper.writeValueAsString(snapshot);
        int updated = mapper.completeResponse(scope.value(), key, httpStatus, body);
        if (updated != 1) {
            throw new IllegalStateException(
                    "幂等记录回填失败：占用行不存在 scope=" + scope.value() + " key=" + key);
        }
        return body;
    }

    private StoredResponse replay(IdempotencyScope scope,
                                  String key,
                                  IdempotencyRecord record,
                                  String inputFingerprint) {
        if (!record.getRequestHash().equals(inputFingerprint)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (record.getHttpStatus() == null || record.getResponseBody() == null) {
            throw new IllegalStateException(
                    "已提交的幂等记录缺少响应快照 scope=" + scope.value() + " key=" + key);
        }
        return new StoredResponse(record.getHttpStatus(), record.getResponseBody());
    }
}
