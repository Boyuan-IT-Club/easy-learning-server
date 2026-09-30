package com.earlylearning.early_learning_server.teacher.infrastructure;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.earlylearning.early_learning_server.teacher.domain.TeacherSummary;

/**
 * 后台教师列表的读模型。
 *
 * <p>为显示激活码尾号与备注，这里只读 JOIN 了 license 模块的 {@code user_license}，不写对方的表；
 * user_license 改列名时要同步这里。keyword 的 LIKE 使用 {@code ESCAPE '!'}。
 */
@Mapper
public interface TeacherQueryMapper {

    String COLUMNS = """
            SELECT u.id, u.username, u.status, l.id AS license_id, l.code_hint AS license_code_hint,
                   l.status AS license_status, l.remark AS license_remark, u.device_id, u.device_bound_at,
                   u.last_refresh_at, u.created_at, u.disabled_reason
              FROM user_account u LEFT JOIN user_license l ON l.user_id = u.id
            """;

    String FILTER = """
            <where>
              <if test="status != null">AND u.status = #{status}</if>
              <if test="pattern != null">AND (u.username LIKE #{pattern} ESCAPE '!'
                                            OR l.code_hint LIKE #{pattern} ESCAPE '!'
                                            OR l.remark LIKE #{pattern} ESCAPE '!')</if>
            </where>
            """;

    @Select("""
            <script>
            """ + COLUMNS + FILTER + """
            ORDER BY u.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<TeacherSummary> selectPage(@Param("status") Integer status,
                                    @Param("pattern") String pattern,
                                    @Param("limit") int limit,
                                    @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM user_account u LEFT JOIN user_license l ON l.user_id = u.id
            """ + FILTER + """
            </script>
            """)
    long countMatching(@Param("status") Integer status, @Param("pattern") String pattern);

    @Select(COLUMNS + " WHERE u.id = #{id}")
    TeacherSummary selectById(@Param("id") int id);
}
