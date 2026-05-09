package ink.icoding.nginx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.sshd")
public class SshConfig {
    private String model;
    private String host;
    private int port = 22;
    private String username;
    private String password;
    private String privateKey;
    private String passphrase;

    public boolean isEnabled() {
        return host != null && !host.isBlank() && username != null && !username.isBlank();
    }

    public boolean isLocalNginx(){
        return !isEnabled() || "local".equalsIgnoreCase(model);
    }
}
