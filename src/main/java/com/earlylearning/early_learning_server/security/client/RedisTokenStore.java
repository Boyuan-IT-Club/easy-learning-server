package com.earlylearning.early_learning_server.security.client;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.ObjectMapper;

/**
 * 教师 access 与管理员 Token 在 Redis 里的读写（Redis 须关闭持久化）。
 *
 * <pre>
 *   el:auth:at:{sha256}             → {user_id, expires_at}       教师 access
 *   el:auth:adt:{sha256}            → {admin_id, expires_at}      管理员 Token
 *   el:auth:adt-admin:{admin_id}    → Set&lt;sha256&gt;                 改密码、停用时一次吊销该管理员全部 Token
 * </pre>
 *
 * <p>access 与管理员 Token 的键比有效期多留一段：到期后一段时间内还能认出"已过期"并回 TOKEN_EXPIRED，
 * 客户端据此去刷新；之后键消失，回 TOKEN_INVALID。
 * Redis 故障统一翻译成 503 DEPENDENCY_UNAVAILABLE：鉴权宁可拒绝，也不放行未经校验的请求。
 */
@Repository
public class RedisTokenStore {

    private static final String TEACHER_KEY = "el:auth:at:";
    private static final String ADMIN_KEY = "el:auth:adt:";
    private static final String ADMIN_INDEX = "el:auth:adt-admin:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisTokenStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public record TeacherGrant(@JsonProperty("user_id") int userId, @JsonProperty("expires_at") Instant expiresAt) {
    }

    public record AdminGrant(@JsonProperty("admin_id") int adminId, @JsonProperty("expires_at") Instant expiresAt) {
    }

    public void saveTeacher(String hash, TeacherGrant grant, Duration keyTtl) {
        String json = objectMapper.writeValueAsString(grant);
        call(() -> {
            redis.opsForValue().set(TEACHER_KEY + hash, json, keyTtl);
            return null;
        });
    }

    public Optional<TeacherGrant> findTeacher(String hash) {
        return read(TEACHER_KEY + hash, TeacherGrant.class);
    }

    public void saveAdmin(String hash, AdminGrant grant, Duration keyTtl) {
        String json = objectMapper.writeValueAsString(grant);
        String index = ADMIN_INDEX + grant.adminId();
        call(() -> {
            redis.opsForValue().set(ADMIN_KEY + hash, json, keyTtl);
            redis.opsForSet().add(index, hash);
            // 索引跟着最新一枚 Token 续期；已过期的成员删除时找不到对应键，无害
            redis.expire(index, keyTtl);
            return null;
        });
    }

    public Optional<AdminGrant> findAdmin(String hash) {
        return read(ADMIN_KEY + hash, AdminGrant.class);
    }

    public void deleteAllForAdmin(int adminId) {
        String index = ADMIN_INDEX + adminId;
        call(() -> {
            Set<String> hashes = redis.opsForSet().members(index);
            if (hashes != null && !hashes.isEmpty()) {
                List<String> keys = new ArrayList<>();
                hashes.forEach(hash -> keys.add(ADMIN_KEY + hash));
                redis.delete(keys);
            }
            redis.delete(index);
            return null;
        });
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        String json = call(() -> redis.opsForValue().get(key));
        return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, type));
    }

    private static <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "鉴权存储暂不可用", e);
        }
    }
}
