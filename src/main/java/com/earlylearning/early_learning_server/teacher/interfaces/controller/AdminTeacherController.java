package com.earlylearning.early_learning_server.teacher.interfaces.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.common.paging.PageQuery;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.common.web.PageResponse;
import com.earlylearning.early_learning_server.teacher.application.TeacherAdminService;
import com.earlylearning.early_learning_server.teacher.domain.TeacherStatus;
import com.earlylearning.early_learning_server.teacher.interfaces.dto.UpdateTeacherStatusRequest;
import com.earlylearning.early_learning_server.teacher.interfaces.dto.UserAccountResponse;

/** 教师云端账号管理（契约"教师管理"）。权限：管理员（由安全链保证）。 */
@RestController
public class AdminTeacherController {

    private final TeacherAdminService adminService;

    public AdminTeacherController(TeacherAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/admin/users")
    public ApiResponse<PageResponse<UserAccountResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status) {
        TeacherStatus filter = status == null ? null : TeacherStatus.of(status, "/parameters/status");
        return ApiResponse.ok(PageResponse.from(
                adminService.list(PageQuery.of(page, pageSize), username, filter), UserAccountResponse::from));
    }

    @PatchMapping("/admin/users/{id}/status")
    public ApiResponse<UserAccountResponse> updateStatus(@PathVariable int id,
                                                         @RequestBody UpdateTeacherStatusRequest request) {
        return ApiResponse.ok(UserAccountResponse.from(
                adminService.updateStatus(id, TeacherStatus.of(request.status(), "/status"))));
    }
}
