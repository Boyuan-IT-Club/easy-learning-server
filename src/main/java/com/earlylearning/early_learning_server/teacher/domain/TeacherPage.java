package com.earlylearning.early_learning_server.teacher.domain;

import java.util.List;

/** 教师分页结果。 */
public record TeacherPage(List<TeacherSummary> items, long total, int page, int size) {
}
