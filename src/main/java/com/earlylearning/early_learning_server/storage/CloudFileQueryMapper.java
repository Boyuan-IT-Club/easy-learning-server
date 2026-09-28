package com.earlylearning.early_learning_server.storage;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文件查询与引用统计的 SQL。
 *
 * <p>keyword 的 LIKE 使用 {@code ESCAPE '!'}：MySQL 在字符串字面量里会吃掉反斜杠，
 * 用 {@code \} 转义会静默失效。
 *
 * <p>分页用显式 LIMIT/OFFSET，不依赖 MyBatis-Plus 的分页插件（缺少该插件时 selectPage 会静默返回全表）。
 */
@Mapper
public interface CloudFileQueryMapper {

    String FILTER = """
            <where>
              <if test="fileCode != null and fileCode != ''">AND file_code = #{fileCode}</if>
              <if test="fileKind != null">AND file_kind = #{fileKind}</if>
              <if test="status != null">AND status = #{status}</if>
              <if test="pattern != null">AND (file_name LIKE #{pattern} ESCAPE '!'
                                            OR file_code LIKE #{pattern} ESCAPE '!')</if>
            </where>
            """;

    @Select("""
            <script>
            SELECT * FROM storage_cloud_file
            """ + FILTER + """
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CloudFile> selectPage(@Param("fileCode") String fileCode,
                               @Param("fileKind") String fileKind,
                               @Param("status") String status,
                               @Param("pattern") String pattern,
                               @Param("limit") int limit,
                               @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM storage_cloud_file
            """ + FILTER + """
            </script>
            """)
    long countMatching(@Param("fileCode") String fileCode,
                       @Param("fileKind") String fileKind,
                       @Param("status") String status,
                       @Param("pattern") String pattern);

    /**
     * 统计给定文件被多少条业务记录引用：语法图标外键 + 课程/评估 JSON 内的编号引用。
     * 不按内容状态过滤，历史与已停用版本里的引用同样计入。
     */
    @Select("""
            <script>
            SELECT f.id AS id,
                   (SELECT COUNT(*) FROM grammar g WHERE g.icon_file_id = f.id)
                 + (SELECT COUNT(*) FROM course c
                    WHERE JSON_SEARCH(c.activity_configs_json, 'all', f.file_code) IS NOT NULL)
                 + (SELECT COUNT(*) FROM assessment_material a
                    WHERE JSON_SEARCH(a.activity_configs_json, 'all', f.file_code) IS NOT NULL)
                   AS reference_count
              FROM storage_cloud_file f
             WHERE f.id IN
             <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<ReferenceCount> countReferences(@Param("ids") List<Integer> ids);

    record ReferenceCount(Integer id, Integer referenceCount) {
    }
}
