package ink.icoding.nginx.cert;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SslCertificateIssueResult {
    private String certificateInfo;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private String lastMessage;
    private String fullchainPem;
    private String privateKeyPem;
    private String certPem;
}
