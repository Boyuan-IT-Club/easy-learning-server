/**
 * 官方评估材料管理与发布。
 *
 * <p>负责前测/初筛/复评材料配置、资源导入和业务校验、版本发布与禁用、版本查询和指定版本配置获取。与 storage 协作处理关联文件；具体评估执行、儿童结果和报告由移动端负责。</p>
 *
 * <p>数据边界：assessment_material。</p>
 * <p>依据：<a href="https://boyuanclub.feishu.cn/wiki/LkqBw1WkPiLL3fk3p6vcqJ1ynHc">03_评估，3.6 评估</a>；
 * 表结构和 JSON 契约以 docs/技术方案.md 为准。</p>
 *
 * <p>Controller、DTO、Service、Mapper、Entity 及子包按实际功能需要创建。</p>
 */
package com.earlylearning.early_learning_server.assessment;

