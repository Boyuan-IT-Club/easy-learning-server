package com.earlylearning.early_learning_server.identity.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.earlylearning.early_learning_server.entity.License;

/**
 * 激活码的读写。状态变更都带旧状态条件：即使上层漏了加锁，也不会把已占用的码再占一次。
 */
@Mapper
public interface LicenseMapper extends BaseMapper<License> {

    /** 注册时锁码：并发注册同一个码时串行化，后到者看到的已是 ACTIVE。 */
    @Select("SELECT * FROM user_license WHERE activation_code_hash = #{hash} FOR UPDATE")
    License selectByHashForUpdate(@Param("hash") String hash);

    @Select("SELECT * FROM user_license WHERE user_id = #{userId}")
    License selectByUserId(@Param("userId") int userId);

    @Select("SELECT * FROM user_license WHERE id = #{id} FOR UPDATE")
    License selectForUpdate(@Param("id") int id);

    @Update("""
            UPDATE user_license SET user_id = #{userId}, status = 'ACTIVE', activated_at = #{at}
             WHERE id = #{id} AND status = 'UNUSED'
            """)
    int markClaimed(@Param("id") int id, @Param("userId") int userId, @Param("at") Instant at);

    @Update("UPDATE user_license SET status = 'REVOKED' WHERE id = #{id} AND status IN ('UNUSED', 'ACTIVE')")
    int markRevoked(@Param("id") int id);

    String FILTER = """
            <where>
              <if test="status != null">AND status = #{status}</if>
              <if test="userId != null">AND user_id = #{userId}</if>
            </where>
            """;

    @Select("""
            <script>
            SELECT * FROM user_license
            """ + FILTER + """
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<License> selectPage(@Param("status") String status,
                             @Param("userId") Integer userId,
                             @Param("limit") int limit,
                             @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM user_license
            """ + FILTER + """
            </script>
            """)
    long countMatching(@Param("status") String status, @Param("userId") Integer userId);
}
