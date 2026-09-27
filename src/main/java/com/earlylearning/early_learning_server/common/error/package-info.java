/**
 * 错误模型：契约的错误码、错误详情与业务异常。
 *
 * <p>不依赖 web 层——适配层用 {@link com.earlylearning.early_learning_server.common.error.BusinessException}
 * 表达依赖失败时不必知道 HTTP；到响应状态码的映射由 {@code common/web} 的全局处理器完成。
 */
package com.earlylearning.early_learning_server.common.error;
