package com.earlylearning.early_learning_server.license.infrastructure;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.earlylearning.early_learning_server.license.domain.License;

/**
 * 激活码的读写。状态变更都用"带旧状态条件"的定点 UPDATE：
 * 即使上层漏了加锁，也不会把已占用的码再占一次，影响行数就是结论。
 */
@Mapper
public interface LicenseMapper extends BaseMapper<License> {

    @Select("SELECT * FROM user_license WHERE activation_code_hash = #{hash}")
    License selectByHash(@Param("hash") String hash);

    /** 注册时锁码：并发注册同一个码时串行化，后到者看到的已是 ACTIVE。 */
    @Select("SELECT * FROM user_license WHERE activation_code_hash = #{hash} FOR UPDATE")
    License selectByHashForUpdate(@Param("hash") String hash);

    @Select("SELECT * FROM user_license WHERE user_id = #{userId}")
    License selectByUserId(@Param("userId") int userId);

    @Select("""
            <script>
            SELECT * FROM user_license WHERE id IN
            <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY id FOR UPDATE
            </script>
            """)
    List<License> selectByIdsForUpdate(@Param("ids") List<Integer> ids);

    @Update("""
            UPDATE user_license SET user_id = #{userId}, status = 'ACTIVE', activated_at = #{at}
             WHERE id = #{id} AND status = 'UNUSED'
            """)
    int markClaimed(@Param("id") int id, @Param("userId") int userId, @Param("at") Instant at);

    @Update("""
            UPDATE user_license
               SET status = 'REVOKED', revoked_by = #{adminId}, revoke_reason = #{reason}, revoked_at = #{at}
             WHERE id = #{id} AND status IN ('UNUSED', 'ACTIVE')
            """)
    int markRevoked(@Param("id") int id, @Param("adminId") int adminId,
                    @Param("reason") String reason, @Param("at") Instant at);
}
