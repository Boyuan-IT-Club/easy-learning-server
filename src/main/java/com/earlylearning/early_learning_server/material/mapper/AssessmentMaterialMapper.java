package com.earlylearning.early_learning_server.material.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.earlylearning.early_learning_server.entity.AssessmentMaterial;

/**
 * 评估材料版本的表访问。通用增查走 MyBatis-Plus 基础方法让枚举 EnumValue 生效；
 * 定点 UPDATE 只改状态列，避免整行写回。
 */
@Mapper
public interface AssessmentMaterialMapper extends BaseMapper<AssessmentMaterial> {

    /**
     * 按稳定编号加当前读锁定全部版本行。编号尚无记录时 InnoDB 会在索引间隙上加间隙锁，
     * 同一编号的并发首发发布因此串行化，保证任一时刻最多一个 ACTIVE 版本。
     */
    @Select("SELECT * FROM assessment_material WHERE official_material_code = #{code} FOR UPDATE")
    List<AssessmentMaterial> selectByCodeForUpdate(@Param("code") String code);

    @Select("SELECT * FROM assessment_material WHERE official_material_code = #{code} AND content_version = #{version}")
    AssessmentMaterial selectByCodeAndVersion(@Param("code") String code, @Param("version") String version);

    @Select("SELECT * FROM assessment_material WHERE id = #{id} FOR UPDATE")
    AssessmentMaterial selectByIdForUpdate(@Param("id") int id);

    /** 只把仍是 ACTIVE 的行置为 DISABLED；返回 0 表示本来就无 ACTIVE 版本。 */
    @Update("UPDATE assessment_material SET status = 'DISABLED' WHERE official_material_code = #{code} AND status = 'ACTIVE'")
    int disableActive(@Param("code") String code);

    @Update("UPDATE assessment_material SET status = 'DISABLED' WHERE id = #{id} AND status = 'ACTIVE'")
    int disableById(@Param("id") int id);
}
