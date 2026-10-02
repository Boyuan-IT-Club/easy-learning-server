package com.earlylearning.early_learning_server.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.earlylearning.early_learning_server.common.enums.CloudFileKind;
import com.earlylearning.early_learning_server.common.enums.CloudFileStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * {@code storage_cloud_file} 的持久化映射。
 *
 * <p>两个标识各管一件事：{@link #fileCode} 是对外身份（终身对应同一份字节、不复用），
 * {@link #objectKey} 是对内物理位置（服务端生成，从不返回客户端）。
 *
 * <p>时间字段交给数据库默认值——留空即由 MyBatis-Plus 排除出 INSERT。
 */
@TableName("storage_cloud_file")
@Getter
@Setter
public class CloudFile {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String fileCode;
    private String objectKey;
    private CloudFileKind fileKind;
    private String fileName;
    private String mimeType;
    private Long sizeBytes;
    private Integer durationMs;
    private CloudFileStatus status;
    private String sha256;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
