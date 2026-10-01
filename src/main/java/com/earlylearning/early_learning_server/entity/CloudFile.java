package com.earlylearning.early_learning_server.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * {@code storage_cloud_file} 的持久化映射。
 *
 * <p>两个标识各管一件事：{@link #fileCode} 是对外身份（终身对应同一份字节、不复用），
 * {@link #objectKey} 是对内物理位置（服务端生成，从不返回客户端）。
 *
 * <p>时间字段交给数据库默认值——留空即由 MyBatis-Plus 排除出 INSERT。
 */
@TableName("storage_cloud_file")
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

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getFileCode() {
        return fileCode;
    }

    public void setFileCode(String fileCode) {
        this.fileCode = fileCode;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public CloudFileKind getFileKind() {
        return fileKind;
    }

    public void setFileKind(CloudFileKind fileKind) {
        this.fileKind = fileKind;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public CloudFileStatus getStatus() {
        return status;
    }

    public void setStatus(CloudFileStatus status) {
        this.status = status;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
