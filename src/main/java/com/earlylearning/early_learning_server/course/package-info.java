/**
 * 官方课程资源管理与发布。
 *
 * <p>负责课程配置、资源导入和业务校验、版本发布与禁用、版本查询和指定版本配置获取。与 storage 协作处理关联文件；课堂实例、私人课程及本地版本切换由移动端负责。</p>
 *
 * <p>数据边界：course。</p>
 * <p>依据：<a href="https://boyuanclub.feishu.cn/wiki/WhWyw6Stgi5OBXkwSlccBMzRnVd">05_课程字典与语法，3.8 课程</a>；
 * 表结构和 JSON 契约以 docs/技术方案.md 为准。</p>
 *
 * <p>Controller、DTO、Service、Mapper、Entity 及子包按实际功能需要创建。</p>
 */
package com.earlylearning.early_learning_server.course;

