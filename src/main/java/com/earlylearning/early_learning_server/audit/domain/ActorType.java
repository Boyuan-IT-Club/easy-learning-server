package com.earlylearning.early_learning_server.audit.domain;

/** 操作者类型。与迁移里的 CHECK 约束一致。 */
public enum ActorType {
    ADMIN,
    TEACHER,
    SYSTEM
}
