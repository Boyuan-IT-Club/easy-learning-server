package com.earlylearning.early_learning_server.license.infrastructure;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.earlylearning.early_learning_server.license.domain.LicenseSummary;

/**
 * 后台激活码列表的读模型。
 *
 * <p>为显示"绑定教师"，这里只读 JOIN 了 teacher 模块的 {@code user_account.username}。
 * 这是读模型对另一张表的只读依赖（与 storage 统计文件引用的做法相同），不写对方的表；
 * user_account 改列名时要同步这里。
 *
 * <p>keyword 的 LIKE 使用 {@code ESCAPE '!'}，理由见 storage 的 CloudFileQueryMapper。
 */
@Mapper
public interface LicenseQueryMapper {

    String FROM = """
            FROM user_license l LEFT JOIN user_account u ON u.id = l.user_id
            <where>
              <if test="status != null">AND l.status = #{status}</if>
              <if test="pattern != null">AND (l.code_hint LIKE #{pattern} ESCAPE '!'
                                            OR l.remark LIKE #{pattern} ESCAPE '!'
                                            OR u.username LIKE #{pattern} ESCAPE '!')</if>
            </where>
            """;

    String COLUMNS = """
            SELECT l.id, l.code_hint, l.status, l.remark, l.user_id, u.username,
                   l.created_at, l.activated_at, l.revoked_at, l.revoke_reason
            """;

    @Select("""
            <script>
            """ + COLUMNS + FROM + """
            ORDER BY l.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<LicenseSummary> selectPage(@Param("status") String status,
                                    @Param("pattern") String pattern,
                                    @Param("limit") int limit,
                                    @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*)
            """ + FROM + """
            </script>
            """)
    long countMatching(@Param("status") String status, @Param("pattern") String pattern);

    @Select("""
            <script>
            """ + COLUMNS + """
            FROM user_license l LEFT JOIN user_account u ON u.id = l.user_id
            WHERE l.id = #{id}
            </script>
            """)
    LicenseSummary selectById(@Param("id") int id);

    @Select("SELECT status, COUNT(*) AS total FROM user_license GROUP BY status")
    List<StatusCount> countByStatus();

    record StatusCount(String status, long total) {
    }
}
