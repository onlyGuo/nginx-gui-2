package ink.icoding.nginx.cert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SslCertificateRenewScheduler {

    private final SslCertificateRepository repository;
    private final SslCertificateService certificateService;

    @Scheduled(cron = "0 20 3 * * *")
    public void renewExpiringCertificates() {
        LocalDateTime threshold = LocalDateTime.now().plusDays(10);
        List<SslCertificate> certificates = repository.findAll().stream()
                .filter(certificate -> Boolean.TRUE.equals(certificate.getAutoRenew()))
                .filter(certificate -> certificate.getExpiresAt() != null)
                .filter(certificate -> !certificate.getExpiresAt().isAfter(threshold))
                .toList();

        for (SslCertificate certificate : certificates) {
            try {
                certificateService.renew(certificate);
            } catch (Exception e) {
                log.warn("SSL 证书自动续签失败: id={}, domains={}", certificate.getId(), certificate.getDomains(), e);
            }
        }
    }
}
