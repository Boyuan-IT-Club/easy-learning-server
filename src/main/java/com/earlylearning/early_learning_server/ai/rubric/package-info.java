/**
 * 评分规则：固定版本与实际采用的评分条目。
 *
 * <p>契约要求所有故事共用同一套长期固定规则，上新故事不改变评分版本；因此版本与条目都由服务端固定配置持有，
 * 不由单次请求决定。
 */
package com.earlylearning.early_learning_server.ai.rubric;
