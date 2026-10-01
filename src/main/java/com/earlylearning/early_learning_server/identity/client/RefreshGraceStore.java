package com.earlylearning.early_learning_server.identity.client;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.identity.model.RefreshGrace;

import tools.jackson.databind.ObjectMapper;

/**
 * 刷新宽限在 Redis 里的读写：{@code el:auth:rt-grace:{旧 refresh 哈希}} → {@link RefreshGrace}，保留 30 秒。
 * 值里有新凭证明文，Redis 须关闭持久化。Redis 故障统一翻译成 503 DEPENDENCY_UNAVAILABLE。
 */
@Repository
public class RefreshGraceStore {

    private static final String KEY = "el:auth:rt-grace:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RefreshGraceStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** 覆盖写：同一枚旧凭证只有在上次轮换回滚后才会再次轮换，此时旧记录必须被替换。 */
    public void remember(String oldRefreshHash, RefreshGrace grace) {
        String json = objectMapper.writeValueAsString(grace);
        try {
            redis.opsForValue().set(KEY + oldRefreshHash, json, RefreshGrace.WINDOW);
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "鉴权存储暂不可用", e);
        }
    }

    public Optional<RefreshGrace> find(String oldRefreshHash) {
        String json;
        try {
            json = redis.opsForValue().get(KEY + oldRefreshHash);
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "鉴权存储暂不可用", e);
        }
        return Optional.ofNullable(json).map(value -> objectMapper.readValue(value, RefreshGrace.class));
    }
}
