package com.earlylearning.early_learning_server.identity.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.common.enums.TeacherStatus;
import com.earlylearning.early_learning_server.common.paging.PageQuery;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.common.web.PageResponse;
import com.earlylearning.early_learning_server.identity.dto.UpdateTeacherStatusRequest;
import com.earlylearning.early_learning_server.identity.dto.UserAccountResponse;
import com.earlylearning.early_learning_server.identity.service.TeacherAdminService;

/** 教师云端账号管理（契约 listTeachers、updateTeacherStatus）。权限：管理员。 */
@RestController
@RequestMapping("/admin/users")
public class TeacherController {

    private final TeacherAdminService teacherAdminService;

    public TeacherController(TeacherAdminService teacherAdminService) {
        this.teacherAdminService = teacherAdminService;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserAccountResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status) {
        TeacherStatus filter = status == null ? null : TeacherStatus.of(status, "/parameters/status");
        return ApiResponse.ok(teacherAdminService.list(PageQuery.of(page, pageSize), username, filter));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<UserAccountResponse> updateStatus(@PathVariable int id,
                                                         @RequestBody UpdateTeacherStatusRequest request) {
        return ApiResponse.ok(teacherAdminService.updateStatus(id, TeacherStatus.of(request.status(), "/status")));
    }
}
