package ink.icoding.nginx.auth;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TokenManager tokenManager;

    @PostConstruct
    public void initDefaultAdmin() {
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(hashPassword("admin"));
            admin.setRole("ADMIN");
            userRepository.save(admin);
        }
    }

    public LoginResult login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!verifyPassword(password, user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = tokenManager.createToken(user);
        return new LoginResult(token, user.getRole());
    }

    public void logout(String token) {
        tokenManager.removeToken(token);
    }

    // ==================== User CRUD ====================

    public List<User> listAll() {
        return userRepository.findAll();
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    public User create(String username, String password, String role) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(hashPassword(password));
        user.setRole(role);
        return userRepository.save(user);
    }

    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("用户不存在");
        }
        userRepository.deleteById(id);
        // Invalidate the deleted user's token immediately
        tokenManager.removeAllTokensByUserId(id);
    }

    // ==================== Password ====================

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = getById(userId);
        if (!verifyPassword(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("原密码错误");
        }
        user.setPassword(hashPassword(newPassword));
        userRepository.save(user);
        // Invalidate all tokens for this user
        tokenManager.removeAllTokensByUserId(userId);
    }

    public void resetPassword(Long userId, String newPassword) {
        User user = getById(userId);
        user.setPassword(hashPassword(newPassword));
        userRepository.save(user);
        // Invalidate all tokens for this user
        tokenManager.removeAllTokensByUserId(userId);
    }

    // ==================== Password Hashing ====================

    public String hashPassword(String rawPassword) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            String saltHex = HexFormat.of().formatHex(salt);
            String hash = sha256(saltHex + rawPassword);
            return saltHex + ":" + hash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public boolean verifyPassword(String rawPassword, String stored) {
        try {
            String[] parts = stored.split(":");
            if (parts.length != 2) return false;
            String saltHex = parts[0];
            String expectedHash = parts[1];
            return sha256(saltHex + rawPassword).equals(expectedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String sha256(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
