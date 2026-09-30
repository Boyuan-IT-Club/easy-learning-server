package com.earlylearning.early_learning_server.auth.infrastructure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.earlylearning.early_learning_server.auth.domain.AdminAccessGrant;
import com.earlylearning.early_learning_server.auth.domain.TeacherAccessGrant;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

import tools.jackson.databind.ObjectMapper;

/**
 * access_token 与 admin_token 在 Redis 里的读写。
 *
 * <pre>
 *   el:auth:at:{sha256}            → TeacherAccessGrant   单枚教师 Token
 *   el:auth:at-user:{user_id}      → Set&lt;sha256&gt;          该教师名下全部 Token，用于停用、解绑时一次吊销
 *   el:auth:adt:{sha256}           → AdminAccessGrant     单枚管理员 Token
 *   el:auth:adt-admin:{admin_id}   → Set&lt;sha256&gt;
 * </pre>
 *
 * <p>键的 TTL 比 Token 有效期多一段保留期：到期后的一段时间内还能认出"这是过期的 Token"并回
 * {@code TOKEN_EXPIRED}，客户端据此自动刷新；保留期过后键消失，回 {@code TOKEN_INVALID}。
 *
 * <p>Redis 故障统一翻译成 503 {@code DEPENDENCY_UNAVAILABLE}：鉴权宁可拒绝，也不放行未经校验的请求。
 */
@Repository
public class RedisTokenRepository {

    private static final String TEACHER_KEY = "el:auth:at:";
    private static final String TEACHER_INDEX = "el:auth:at-user:";
    private static final String ADMIN_KEY = "el:auth:adt:";
    private static final String ADMIN_INDEX = "el:auth:adt-admin:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisTokenRepository(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void saveTeacher(String hash, TeacherAccessGrant grant, Duration keyTtl) {
        save(TEACHER_KEY + hash, TEACHER_INDEX + grant.userId(), hash, grant, keyTtl);
    }

    public Optional<TeacherAccessGrant> findTeacher(String hash) {
        return find(TEACHER_KEY + hash, TeacherAccessGrant.class);
    }

    public void deleteAllForTeacher(int userId) {
        deleteAll(TEACHER_KEY, TEACHER_INDEX + userId, null);
    }

    public void saveAdmin(String hash, AdminAccessGrant grant, Duration keyTtl) {
        save(ADMIN_KEY + hash, ADMIN_INDEX + grant.adminId(), hash, grant, keyTtl);
    }

    public Optional<AdminAccessGrant> findAdmin(String hash) {
        return find(ADMIN_KEY + hash, AdminAccessGrant.class);
    }

    public void deleteAdmin(int adminId, String hash) {
        call(() -> {
            redis.delete(ADMIN_KEY + hash);
            redis.opsForSet().remove(ADMIN_INDEX + adminId, hash);
            return null;
        });
    }

    /** @param keepHash 保留的那一枚（例如改密码时当前正在用的），可为 null */
    public void deleteAllForAdmin(int adminId, String keepHash) {
        deleteAll(ADMIN_KEY, ADMIN_INDEX + adminId, keepHash);
    }

    private void save(String key, String indexKey, String hash, Object grant, Duration keyTtl) {
        String json = objectMapper.writeValueAsString(grant);
        call(() -> {
            redis.opsForValue().set(key, json, keyTtl);
            redis.opsForSet().add(indexKey, hash);
            // 索引跟着最新一枚 Token 续期；过期的成员删除时找不到对应键，无害
            redis.expire(indexKey, keyTtl);
            return null;
        });
    }

    private <T> Optional<T> find(String key, Class<T> type) {
        String json = call(() -> redis.opsForValue().get(key));
        return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, type));
    }

    private void deleteAll(String keyPrefix, String indexKey, String keepHash) {
        call(() -> {
            Set<String> hashes = redis.opsForSet().members(indexKey);
            if (hashes == null || hashes.isEmpty()) {
                return null;
            }
            List<String> keys = new ArrayList<>();
            for (String hash : hashes) {
                if (!hash.equals(keepHash)) {
                    keys.add(keyPrefix + hash);
                    redis.opsForSet().remove(indexKey, hash);
                }
            }
            if (!keys.isEmpty()) {
                redis.delete(keys);
            }
            return null;
        });
    }

    private static <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "鉴权存储暂不可用", e);
        }
    }
}
