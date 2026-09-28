package com.earlylearning.early_learning_server.common.idempotency;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * {@code idempotency_record} 的持久化映射。
 *
 * <p>{@code httpStatus} / {@code responseBody} 占用时留空、业务成功后在同一事务内回填，
 * 因此对其它事务而言这两个字段恒有值。时间字段交给数据库默认值：留空即由 MyBatis-Plus 排除出 INSERT。
 */
@TableName("idempotency_record")
public class IdempotencyRecord {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String scope;
    private String idempotencyKey;
    private String requestHash;
    private Integer httpStatus;
    private String responseBody;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 占用用的新行，响应快照留空由回填步骤补上。 */
    static IdempotencyRecord claimed(String scope, String key, String requestHash) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.scope = scope;
        record.idempotencyKey = key;
        record.requestHash = requestHash;
        return record;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
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
