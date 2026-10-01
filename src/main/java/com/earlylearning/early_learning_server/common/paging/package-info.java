/**
 * 分页查询参数（page 从 1 开始、page_size 最大 100）与 LIKE 转义。分页响应形状见 {@code common.web.PageResponse}。
 * 整个子包都是对外 API（@NamedInterface）。
 */
@org.springframework.modulith.NamedInterface("paging")
package com.earlylearning.early_learning_server.common.paging;
