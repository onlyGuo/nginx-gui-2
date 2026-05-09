package ink.icoding.nginx.web;

import ink.icoding.nginx.auth.LoginRequest;
import ink.icoding.nginx.auth.LoginResult;
import ink.icoding.nginx.auth.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody LoginRequest request) {
        try {
            LoginResult result = userService.login(request.getUsername(), request.getPassword());
            return ApiResponse.ok(Map.of(
                    "token", result.token(),
                    "username", request.getUsername(),
                    "role", result.role()
            ));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(401, e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            userService.logout(authHeader.substring(7));
        }
        return ApiResponse.ok();
    }
}
