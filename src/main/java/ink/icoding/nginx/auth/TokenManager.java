package ink.icoding.nginx.auth;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenManager {

    private static final int TOKEN_EXPIRY_DAYS = 30;

    private final ConcurrentHashMap<String, TokenInfo> tokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> userIdToToken = new ConcurrentHashMap<>();

    public String createToken(User user) {
        // Evict old token for this user (single-session enforcement)
        String oldToken = userIdToToken.remove(user.getId());
        if (oldToken != null) {
            tokens.remove(oldToken);
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        TokenInfo info = new TokenInfo(user.getId(), user.getUsername(), user.getRole());
        tokens.put(token, info);
        userIdToToken.put(user.getId(), token);
        return token;
    }

    public TokenInfo validateToken(String token) {
        if (token == null || token.isBlank()) return null;
        TokenInfo info = tokens.get(token);
        if (info == null) return null;
        if (info.getCreatedAt().plusDays(TOKEN_EXPIRY_DAYS).isBefore(LocalDateTime.now())) {
            removeToken(token);
            return null;
        }
        return info;
    }

    public void removeToken(String token) {
        TokenInfo info = tokens.remove(token);
        if (info != null) {
            userIdToToken.remove(info.getUserId(), token);
        }
    }

    public void removeAllTokensByUserId(Long userId) {
        String token = userIdToToken.remove(userId);
        if (token != null) {
            tokens.remove(token);
        }
    }
}
