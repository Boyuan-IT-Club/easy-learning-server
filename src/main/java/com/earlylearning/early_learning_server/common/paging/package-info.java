/**
 * 分页查询的入参与结果：应用层使用，不认识 HTTP；转成响应形状是各模块 interfaces 层的事。
 * 整个子包都是对外 API（@NamedInterface）。
 */
@org.springframework.modulith.NamedInterface("paging")
package com.earlylearning.early_learning_server.common.paging;
