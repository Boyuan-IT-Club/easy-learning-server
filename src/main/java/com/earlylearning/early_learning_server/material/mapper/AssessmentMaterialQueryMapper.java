package com.earlylearning.early_learning_server.material.mapper;

import java.util.List;

import com.earlylearning.early_learning_server.entity.AssessmentMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 评估材料的只读查询：管理端筛选分页与平板端版本目录。
 * 显式 LIMIT OFFSET，不依赖 MyBatis-Plus 分页插件。
 */
@Mapper
public interface AssessmentMaterialQueryMapper {

    String FILTER = """
            <where>
              <if test="code != null">AND official_material_code = #{code}</if>
              <if test="status != null">AND status = #{status}</if>
              <if test="pattern != null">AND name LIKE #{pattern} ESCAPE '!'</if>
            </where>
            """;

    @Select("""
            <script>
            SELECT * FROM assessment_material
            """ + FILTER + """
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AssessmentMaterial> selectPage(@Param("code") String code,
                                        @Param("status") String status,
                                        @Param("pattern") String pattern,
                                        @Param("limit") int limit,
                                        @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM assessment_material
            """ + FILTER + """
            </script>
            """)
    long countMatching(@Param("code") String code,
                       @Param("status") String status,
                       @Param("pattern") String pattern);

    /** 平板端版本目录：按稳定编号升序、创建先后升序，含 DISABLED。 */
    @Select("SELECT * FROM assessment_material ORDER BY official_material_code ASC, id ASC")
    List<AssessmentMaterial> selectAllOrdered();
}
