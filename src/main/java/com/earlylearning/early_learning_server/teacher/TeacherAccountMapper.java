package com.earlylearning.early_learning_server.teacher;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 教师账号的读写。
 *
 * <p>状态与刷新凭证都用定点 UPDATE：MyBatis-Plus 的 updateById 默认跳过 null，也会把整行写回。
 * 分页用显式 LIMIT/OFFSET，与 storage 一致；LIKE 使用 {@code ESCAPE '!'}。
 */
@Mapper
public interface TeacherAccountMapper extends BaseMapper<TeacherAccount> {

    @Select("SELECT * FROM user_account WHERE username = #{username}")
    TeacherAccount selectByUsername(@Param("username") String username);

    @Select("SELECT * FROM user_account WHERE id = #{id} FOR UPDATE")
    TeacherAccount selectForUpdate(@Param("id") int id);

    @Select("SELECT * FROM user_account WHERE refresh_token_hash = #{hash} FOR UPDATE")
    TeacherAccount selectByRefreshHashForUpdate(@Param("hash") String hash);

    @Update("UPDATE user_account SET refresh_token_hash = #{hash} WHERE id = #{id}")
    int updateRefreshHash(@Param("id") int id, @Param("hash") String hash);

    /** 条件轮换：旧哈希仍是当前值才更新，并发刷新只有一个成功。 */
    @Update("UPDATE user_account SET refresh_token_hash = #{newHash} WHERE id = #{id} AND refresh_token_hash = #{oldHash}")
    int rotateRefresh(@Param("id") int id, @Param("oldHash") String oldHash, @Param("newHash") String newHash);

    @Update("UPDATE user_account SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") int id, @Param("status") int status);

    String FILTER = """
            <where>
              <if test="pattern != null">AND username LIKE #{pattern} ESCAPE '!'</if>
              <if test="status != null">AND status = #{status}</if>
            </where>
            """;

    @Select("""
            <script>
            SELECT * FROM user_account
            """ + FILTER + """
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<TeacherAccount> selectPage(@Param("pattern") String pattern,
                                    @Param("status") Integer status,
                                    @Param("limit") int limit,
                                    @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM user_account
            """ + FILTER + """
            </script>
            """)
    long countMatching(@Param("pattern") String pattern, @Param("status") Integer status);
}
