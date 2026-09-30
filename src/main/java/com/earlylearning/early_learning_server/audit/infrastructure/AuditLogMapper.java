package com.earlylearning.early_learning_server.audit.infrastructure;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.earlylearning.early_learning_server.audit.domain.AuditLog;

/**
 * 审计记录：只追加与查询。
 *
 * <p>分页用显式 LIMIT/OFFSET，与 storage 一致，不依赖 MyBatis-Plus 分页插件。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    String FILTER = """
            <where>
              <if test="action != null">AND action = #{action}</if>
              <if test="targetType != null">AND target_type = #{targetType}</if>
              <if test="targetId != null and targetId != ''">AND target_id = #{targetId}</if>
              <if test="from != null">AND created_at &gt;= #{from}</if>
              <if test="to != null">AND created_at &lt; #{to}</if>
            </where>
            """;

    @Select("""
            <script>
            SELECT * FROM audit_log
            """ + FILTER + """
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AuditLog> selectPage(@Param("action") String action,
                              @Param("targetType") String targetType,
                              @Param("targetId") String targetId,
                              @Param("from") Instant from,
                              @Param("to") Instant to,
                              @Param("limit") int limit,
                              @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM audit_log
            """ + FILTER + """
            </script>
            """)
    long countMatching(@Param("action") String action,
                       @Param("targetType") String targetType,
                       @Param("targetId") String targetId,
                       @Param("from") Instant from,
                       @Param("to") Instant to);
}
