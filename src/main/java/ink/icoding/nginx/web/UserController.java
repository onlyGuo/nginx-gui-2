package ink.icoding.nginx.web;

import ink.icoding.nginx.auth.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        User user = userService.getById(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("role", user.getRole());
        data.put("createdAt", user.getCreatedAt());
        return ApiResponse.ok(data);
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        if (!isAdmin(request)) return ApiResponse.error(403, "需要管理员权限");
        List<Map<String, Object>> users = userService.listAll().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("role", u.getRole());
            m.put("createdAt", u.getCreatedAt());
            return m;
        }).toList();
        return ApiResponse.ok(users);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody CreateUserRequest body, HttpServletRequest request) {
        if (!isAdmin(request)) return ApiResponse.error(403, "需要管理员权限");
        try {
            User user = userService.create(body.getUsername(), body.getPassword(), "MANAGER");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", user.getId());
            data.put("username", user.getUsername());
            data.put("role", user.getRole());
            return ApiResponse.ok(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        if (!isAdmin(request)) return ApiResponse.error(403, "需要管理员权限");
        try {
            userService.delete(id);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}/password")
    public ApiResponse<Void> changePassword(@PathVariable Long id, @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        // MANAGER can only change own password; ADMIN can change anyone's
        if ("MANAGER".equals(role) && !currentUserId.equals(id)) {
            return ApiResponse.error(403, "管理员只能修改自己的密码");
        }
        try {
            userService.changePassword(id, body.getOldPassword(), body.getNewPassword());
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest body, HttpServletRequest request) {
        if (!isAdmin(request)) return ApiResponse.error(403, "需要管理员权限");
        try {
            userService.resetPassword(id, body.getNewPassword());
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    private boolean isAdmin(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        return "ADMIN".equals(role);
    }
}
