package com.earlylearning.early_learning_server.material.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.earlylearning.early_learning_server.material.model.GrammarDefinition;

/**
 * 语法条目的只读查询。语法要素模块尚未建管理接口，评估材料发布校验与依赖展开
 * 需要按编号读当前定义，这里直接查表；语法模块落地后应改走它暴露的接口。
 * 图标编号通过关联 storage_cloud_file 得到，不落图标文件本体。
 */
@Mapper
public interface GrammarRefMapper {

    @Select("""
            <script>
            SELECT g.grammar_code, g.name, g.version, f.file_code AS icon_file_code,
                   g.status, g.created_at, g.updated_at
            FROM grammar g LEFT JOIN storage_cloud_file f ON f.id = g.icon_file_id
            WHERE g.grammar_code IN
            <foreach collection="codes" item="code" open="(" separator="," close=")">#{code}</foreach>
            </script>
            """)
    List<GrammarDefinition> selectByCodes(@Param("codes") Collection<String> codes);
}
