package com.earlylearning.early_learning_server.teacher.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.earlylearning.early_learning_server.common.web.ApiResponse;
import com.earlylearning.early_learning_server.common.web.PageParams;
import com.earlylearning.early_learning_server.teacher.TeacherAdminService;
import com.earlylearning.early_learning_server.teacher.TeacherStatus;

/** 教师云端账号管理（契约"教师管理"）。权限：管理员（由安全链保证）。 */
@RestController
public class AdminTeacherController {

    private final TeacherAdminService adminService;

    public AdminTeacherController(TeacherAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/admin/users")
    public ApiResponse<TeacherPageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status) {
        TeacherStatus filter = status == null ? null : TeacherStatus.of(status, "/parameters/status");
        return ApiResponse.ok(adminService.list(PageParams.of(page, pageSize), username, filter));
    }

    @PatchMapping("/admin/users/{id}/status")
    public ApiResponse<UserAccountResponse> updateStatus(@PathVariable int id,
                                                         @RequestBody UpdateTeacherStatusRequest request) {
        return ApiResponse.ok(adminService.updateStatus(id, TeacherStatus.of(request.status(), "/status")));
    }
}
