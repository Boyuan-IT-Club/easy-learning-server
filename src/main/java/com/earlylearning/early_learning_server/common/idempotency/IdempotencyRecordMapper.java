package com.earlylearning.early_learning_server.common.idempotency;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 幂等记录的读写。并发语义见 {@link IdempotencyService}。 */
@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecord> {

    /** 快路径：命中即重放或判冲突。 */
    @Select("SELECT id, scope, idempotency_key, request_hash, http_status, response_body, created_at, updated_at "
            + "FROM idempotency_record WHERE scope = #{scope} AND idempotency_key = #{key}")
    IdempotencyRecord find(@Param("scope") String scope, @Param("key") String key);

    /**
     * 当前读（加行锁）。
     *
     * <p>并发同键时，快照读可能看不到对方刚提交的那一行，必须用当前读把胜者取回来。
     */
    @Select("SELECT id, scope, idempotency_key, request_hash, http_status, response_body, created_at, updated_at "
            + "FROM idempotency_record WHERE scope = #{scope} AND idempotency_key = #{key} FOR UPDATE")
    IdempotencyRecord findForUpdate(@Param("scope") String scope, @Param("key") String key);

    /** 回填首次响应，返回受影响行数（应为 1）。 */
    @Update("UPDATE idempotency_record SET http_status = #{httpStatus}, response_body = #{responseBody} "
            + "WHERE scope = #{scope} AND idempotency_key = #{key}")
    int completeResponse(@Param("scope") String scope,
                         @Param("key") String key,
                         @Param("httpStatus") int httpStatus,
                         @Param("responseBody") String responseBody);
}
