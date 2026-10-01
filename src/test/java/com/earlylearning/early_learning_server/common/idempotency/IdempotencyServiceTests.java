package com.earlylearning.early_learning_server.common.idempotency;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 幂等机制的真实库行为。
 *
 * <p>只保留事务语义下才成立的用例：唯一索引挡并发、回滚后键可重试、脱离事务直接拒绝。
 * 这些都是内存假实现验证不了的，也是这套机制存在的理由。
 */
@SpringBootTest
class IdempotencyServiceTests {

    private static final IdempotencyScope SCOPE = IdempotencyScope.ADMIN_FILE_UPLOAD;
    private static final String KEY_PREFIX = "idem-test-";

    @Autowired
    private IdempotencyService idempotency;

    @Autowired
    private IdempotencyRecordMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void removeTestRecords() {
        jdbc.update("DELETE FROM idempotency_record WHERE idempotency_key LIKE ?", KEY_PREFIX + "%");
    }

    @Test
    void callOutsideTransactionIsRejected() {
        assertThrows(IllegalTransactionStateException.class,
                () -> idempotency.claim(SCOPE, newKey(), InputFingerprint.of("input-a")));
    }

    @Test
    void sameKeyAndInputReplaysFirstResponse() {
        String key = newKey();
        AtomicInteger executed = new AtomicInteger();

        StoredResponse first = executeOnce(key, "input-a", executed);
        StoredResponse second = executeOnce(key, "input-a", executed);

        assertEquals(1, executed.get());
        assertEquals(201, second.httpStatus());
        assertEquals(first.body(), second.body());
    }

    @Test
    void sameKeyWithDifferentInputReturns409() {
        String key = newKey();
        executeOnce(key, "input-a", new AtomicInteger());

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> executeOnce(key, "input-b", new AtomicInteger()));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, conflict.getErrorCode());
        assertEquals(409, conflict.getHttpStatus().value());
    }

    @Test
    void concurrentSameKeyRunsBusinessOnce() throws Exception {
        String key = newKey();
        AtomicInteger executed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<StoredResponse> submit = () -> {
                start.await();
                return executeOnce(key, "input-a", executed);
            };
            Future<StoredResponse> first = pool.submit(submit);
            Future<StoredResponse> second = pool.submit(submit);
            start.countDown();

            StoredResponse a = first.get(30, TimeUnit.SECONDS);
            StoredResponse b = second.get(30, TimeUnit.SECONDS);

            assertEquals(1, executed.get());
            assertEquals(201, a.httpStatus());
            assertEquals(a.body(), b.body());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void keyIsReusableAfterFailureRollback() {
        String key = newKey();
        AtomicInteger executed = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            idempotency.claim(SCOPE, key, InputFingerprint.of("input-a"));
            executed.incrementAndGet();
            throw new IllegalStateException("业务写入失败");
        }));

        assertNull(mapper.find(SCOPE.value(), key), "回滚后不应残留占用行");

        StoredResponse retried = executeOnce(key, "input-a", executed);
        assertEquals(201, retried.httpStatus());
        assertEquals(2, executed.get(), "回滚掉的那次不算执行过");
    }

    /** 走一遍「占用 → 业务写入 → 回填」，返回与接口实际返回一致的响应快照。 */
    private StoredResponse executeOnce(String key, String input, AtomicInteger executed) {
        return transaction.execute(status -> {
            Optional<StoredResponse> replayed = idempotency.claim(SCOPE, key, InputFingerprint.of(input));
            if (replayed.isPresent()) {
                return replayed.get();
            }
            executed.incrementAndGet();
            String body = idempotency.record(SCOPE, key, 201,
                    Map.of("file_code", "LF_TEST"));
            return new StoredResponse(201, body);
        });
    }

    private String newKey() {
        return KEY_PREFIX + UUID.randomUUID();
    }
}
