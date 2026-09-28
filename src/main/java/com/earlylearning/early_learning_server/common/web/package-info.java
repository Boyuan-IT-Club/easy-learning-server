/**
 * 对外 HTTP 响应契约：统一包络、错误码、异常基类与全局异常处理。
 *
 * <p>所有 Controller 的响应形状由这里决定；业务代码只需抛 {@link com.earlylearning.early_learning_server.common.error.BusinessException}。
 */
package com.earlylearning.early_learning_server.common.web;

import com.earlylearning.early_learning_server.common.error.BusinessException;