package com.earlylearning.early_learning_server.auth.infrastructure;

import java.time.Duration;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.earlylearning.early_learning_server.auth.domain.TokenPair;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

import tools.jackson.databind.ObjectMapper;

/**
 * 刷新宽限：{@code el:auth:rt-grace:{旧 refresh 哈希}} → 轮换得到的新 Token 对（明文）。
 *
 * <p>契约 v1.6.0：旧凭证在短窗口内重试时返回同一组新凭证，不再轮换、不延长窗口。
 * 用 SET NX 写入，窗口只从第一次轮换开始算。值是明文，Redis 必须关闭持久化。
 */
@Repository
public class RedisRefreshGraceRepository {

    private static final String KEY = "el:auth:rt-grace:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisRefreshGraceRepository(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void remember(String oldRefreshHash, TokenPair pair, Duration grace) {
        String json = objectMapper.writeValueAsString(pair);
        try {
            redis.opsForValue().setIfAbsent(KEY + oldRefreshHash, json, grace);
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "鉴权存储暂不可用", e);
        }
    }

    public Optional<TokenPair> find(String oldRefreshHash) {
        String json;
        try {
            json = redis.opsForValue().get(KEY + oldRefreshHash);
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "鉴权存储暂不可用", e);
        }
        return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, TokenPair.class));
    }
}
