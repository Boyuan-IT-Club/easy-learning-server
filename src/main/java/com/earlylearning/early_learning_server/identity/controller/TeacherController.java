package com.earlylearning.early_learning_server.identity.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.common.paging.PageQuery;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.common.web.PageResponse;
import com.earlylearning.early_learning_server.identity.dto.UpdateTeacherStatusRequest;
import com.earlylearning.early_learning_server.identity.dto.UserAccountResponse;
import com.earlylearning.early_learning_server.identity.entity.TeacherStatus;
import com.earlylearning.early_learning_server.identity.service.TeacherAdminService;

/** 教师云端账号管理（契约 listTeachers、updateTeacherStatus）。权限：管理员。 */
@RestController
public class TeacherController {

    private final TeacherAdminService adminService;

    public TeacherController(TeacherAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/admin/users")
    public ApiResponse<PageResponse<UserAccountResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status) {
        TeacherStatus filter = status == null ? null : TeacherStatus.of(status, "/parameters/status");
        return ApiResponse.ok(adminService.list(PageQuery.of(page, pageSize), username, filter));
    }

    @PatchMapping("/admin/users/{id}/status")
    public ApiResponse<UserAccountResponse> updateStatus(@PathVariable int id,
                                                         @RequestBody UpdateTeacherStatusRequest request) {
        return ApiResponse.ok(adminService.updateStatus(id, TeacherStatus.of(request.status(), "/status")));
    }
}
