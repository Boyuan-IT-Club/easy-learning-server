package com.earlylearning.early_learning_server.teacher.infrastructure;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.earlylearning.early_learning_server.teacher.domain.TeacherAccount;

/**
 * 教师账号的读写。
 *
 * <p>更新都写成定点 UPDATE：MyBatis-Plus 的 updateById 默认跳过 null 字段，没法把设备号、恢复码"清空"。
 */
@Mapper
public interface TeacherAccountMapper extends BaseMapper<TeacherAccount> {

    @Select("SELECT * FROM user_account WHERE username = #{username}")
    TeacherAccount selectByUsername(@Param("username") String username);

    @Select("SELECT * FROM user_account WHERE id = #{id} FOR UPDATE")
    TeacherAccount selectForUpdate(@Param("id") int id);

    @Select("SELECT * FROM user_account WHERE device_id = #{deviceId}")
    TeacherAccount selectByDeviceId(@Param("deviceId") String deviceId);

    @Select("SELECT * FROM user_account WHERE refresh_token_hash = #{hash}")
    TeacherAccount selectByRefreshHash(@Param("hash") String hash);

    /** 条件轮换：旧哈希仍是当前值才更新。并发刷新时只有一个成功，另一个去读宽限缓存。 */
    @Update("""
            UPDATE user_account SET refresh_token_hash = #{newHash}, last_refresh_at = #{at}
             WHERE id = #{id} AND refresh_token_hash = #{oldHash}
            """)
    int rotateRefresh(@Param("id") int id, @Param("oldHash") String oldHash,
                      @Param("newHash") String newHash, @Param("at") Instant at);

    @Update("UPDATE user_account SET refresh_token_hash = #{hash} WHERE id = #{id}")
    int updateRefreshHash(@Param("id") int id, @Param("hash") String hash);

    @Update("UPDATE user_account SET status = #{status}, disabled_reason = #{reason} WHERE id = #{id}")
    int updateStatus(@Param("id") int id, @Param("status") int status, @Param("reason") String reason);

    @Update("""
            UPDATE user_account
               SET device_id = #{deviceId}, device_bound_at = #{boundAt}, refresh_token_hash = #{refreshHash}
             WHERE id = #{id}
            """)
    int updateBinding(@Param("id") int id, @Param("deviceId") String deviceId,
                      @Param("boundAt") Instant boundAt, @Param("refreshHash") String refreshHash);

    @Update("""
            UPDATE user_account
               SET recovery_code_hash = #{hash}, recovery_code_expires_at = #{expiresAt},
                   recovery_code_failed_count = #{failedCount}
             WHERE id = #{id}
            """)
    int updateRecoveryCode(@Param("id") int id, @Param("hash") String hash,
                           @Param("expiresAt") Instant expiresAt, @Param("failedCount") int failedCount);
}
