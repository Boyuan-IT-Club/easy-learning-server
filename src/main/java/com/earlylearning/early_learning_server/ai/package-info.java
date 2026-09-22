/**
 * AI 转写与评分能力预留。
 *
 * <p>规划承接转写、故事评分、逐题问答评分、评分规则及模型/Prompt 版本管理。具体业务结果由移动端保存；当前仅预留模块，任务持久化设计明确后再实现异步任务及查询接口。</p>
 *
 * <p>数据边界：任务存储尚未设计，不新增数据库表。</p>
 * <p>依据：<a href="https://boyuanclub.feishu.cn/wiki/XTvkwj6pHimslckweHKcaxmQngh">06_文件同步与AI，3.16 AI / 转写</a>；
 * 表结构和 JSON 契约以 docs/技术方案.md 为准。</p>
 *
 * <p>Controller、DTO、Service、Mapper、Entity 及子包按实际功能需要创建。</p>
 */
package com.earlylearning.early_learning_server.ai;

