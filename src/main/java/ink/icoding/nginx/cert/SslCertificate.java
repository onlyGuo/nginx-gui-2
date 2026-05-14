package ink.icoding.nginx.cert;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ssl_certificate")
public class SslCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String issuer = "Let's Encrypt";

    @Column(nullable = false, length = 128)
    private String accountName;

    @Column(length = 512)
    private String remark;

    @Column(nullable = false, length = 512)
    private String domains;

    @Column(nullable = false, length = 512)
    private String storagePath;

    @Column(nullable = false, length = 64)
    private String dnsProvider;

    @JsonIgnore
    @Column(length = 512)
    private String dnsCredentialId;

    @JsonIgnore
    @Column(length = 1024)
    private String dnsCredentialSecret;

    @JsonIgnore
    @Column(length = 1024)
    private String dnsCredentialToken;

    @Column(nullable = false)
    private Boolean autoRenew = true;

    @Column(length = 1024)
    private String certificateInfo;

    private LocalDateTime issuedAt;

    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SslCertificateStatus status = SslCertificateStatus.PENDING;

    @Column(length = 4096)
    private String lastMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = SslCertificateStatus.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            status = SslCertificateStatus.EXPIRED;
        }
    }
}
