package com.earlylearning.early_learning_server.storage.mapper;
import com.earlylearning.early_learning_server.storage.entity.CloudFile;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 云端文件元数据的读写。
 *
 * <p>用 MyBatis-Plus 的通用方法，以便枚举字段上的 {@code @EnumValue} 生效。
 */
@Mapper
public interface CloudFileMapper extends BaseMapper<CloudFile> {

    /**
     * 按编号取行并加排他锁。
     *
     * <p>用于串行化并发删除。注意它挡不住并发新增引用：持有该行的锁期间，
     * 别的会话仍能插入引用它的记录。
     */
    @Select("SELECT * FROM storage_cloud_file WHERE file_code = #{fileCode} FOR UPDATE")
    CloudFile selectForUpdate(@Param("fileCode") String fileCode);

    /** 只改状态。用定点 UPDATE 而不是 updateById，避免把整行其它字段一并写回。 */
    @Update("UPDATE storage_cloud_file SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Integer id, @Param("status") String status);
}
