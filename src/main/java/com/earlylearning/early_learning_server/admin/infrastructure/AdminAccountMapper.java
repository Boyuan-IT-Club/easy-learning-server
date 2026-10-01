package com.earlylearning.early_learning_server.admin.infrastructure;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.earlylearning.early_learning_server.admin.domain.AdminAccount;

/**
 * 管理员账号的读写。密码与状态用定点 UPDATE；分页用显式 LIMIT/OFFSET，与 storage 一致。
 * 本 Mapper 会读到 password_hash，日志级别不得开到 DEBUG（logback-spring.xml 已单独压低）。
 */
@Mapper
public interface AdminAccountMapper extends BaseMapper<AdminAccount> {

    @Select("SELECT * FROM admin_account WHERE username = #{username}")
    AdminAccount selectByUsername(@Param("username") String username);

    @Select("SELECT * FROM admin_account WHERE id = #{id} FOR UPDATE")
    AdminAccount selectForUpdate(@Param("id") int id);

    @Select("SELECT COUNT(*) FROM admin_account")
    long countAll();

    @Update("UPDATE admin_account SET password_hash = #{hash} WHERE id = #{id}")
    int updatePassword(@Param("id") int id, @Param("hash") String hash);

    @Update("UPDATE admin_account SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") int id, @Param("status") String status);

    String FILTER = """
            <where>
              <if test="username != null">AND username = #{username}</if>
              <if test="status != null">AND status = #{status}</if>
            </where>
            """;

    @Select("""
            <script>
            SELECT * FROM admin_account
            """ + FILTER + """
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminAccount> selectPage(@Param("username") String username,
                                  @Param("status") String status,
                                  @Param("limit") int limit,
                                  @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM admin_account
            """ + FILTER + """
            </script>
            """)
    long countMatching(@Param("username") String username, @Param("status") String status);
}
