/**
 * 事务边界辅助：把"数据库之外的副作用"（写 Redis、删缓存）推迟到事务提交之后。
 * 整个子包都是对外 API（@NamedInterface）。
 */
@org.springframework.modulith.NamedInterface("tx")
package com.earlylearning.early_learning_server.common.tx;
