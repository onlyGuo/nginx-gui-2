package ink.icoding.nginx.auth;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TokenInfo {
    private final Long userId;
    private final String username;
    private final String role;
    private final LocalDateTime createdAt = LocalDateTime.now();
}
