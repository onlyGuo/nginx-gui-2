package ink.icoding.nginx.cert;

import ink.icoding.nginx.core.BadRequestException;
import ink.icoding.nginx.utils.FileUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SslCertificateService {

    private static final String ISSUER = "Let's Encrypt";

    private final SslCertificateRepository repository;
    private final NativeAcmeClient nativeAcmeClient;
    private final TencentDnsChallengeService tencentDnsChallengeService;

    @Transactional
    public SslCertificate issue(SslCertificateRequest request) {
        validate(request);

        SslCertificate certificate = new SslCertificate();
        certificate.setIssuer(ISSUER);
        certificate.setAccountName(request.getAccountName().trim());
        certificate.setRemark(trimToEmpty(request.getRemark()));
        certificate.setDomains(normalizeDomains(request.getDomains()));
        certificate.setStoragePath(resolveStoragePath(request.getStoragePath(), certificate.getDomains()));
        certificate.setDnsProvider(request.getDnsProvider().trim());
        certificate.setDnsCredentialId(trimToEmpty(request.getDnsCredentialId()));
        certificate.setDnsCredentialSecret(trimToEmpty(request.getDnsCredentialSecret()));
        certificate.setDnsCredentialToken(trimToEmpty(request.getDnsCredentialToken()));
        certificate.setAutoRenew(request.getAutoRenew() == null || request.getAutoRenew());
        certificate.setStatus(SslCertificateStatus.PENDING);
        certificate.setLastMessage("正在通过内置 ACME 客户端申请证书");
        certificate = repository.save(certificate);

        try {
            SslCertificateIssueResult result = issueNative(certificate);
            applyIssueResult(certificate, result);
            return repository.save(certificate);
        } catch (RuntimeException e) {
            certificate.setStatus(SslCertificateStatus.FAILED);
            certificate.setLastMessage(e.getMessage());
            repository.save(certificate);
            throw e;
        }
    }

    @Transactional
    public SslCertificate renew(SslCertificate certificate) {
        certificate.setStatus(SslCertificateStatus.PENDING);
        certificate.setLastMessage("正在通过内置 ACME 客户端续签证书");
        repository.save(certificate);

        try {
            SslCertificateIssueResult result = issueNative(certificate);
            applyIssueResult(certificate, result);
        } catch (RuntimeException e) {
            certificate.setStatus(SslCertificateStatus.FAILED);
            certificate.setLastMessage(e.getMessage());
        }
        return repository.save(certificate);
    }

    @Transactional
    public SslCertificate updateAutoRenew(Long id, boolean autoRenew) {
        SslCertificate certificate = repository.findById(id)
                .orElseThrow(() -> new BadRequestException("证书不存在"));
        certificate.setAutoRenew(autoRenew);
        return repository.save(certificate);
    }

    public Map<String, Object> acmeStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("installed", true);
        data.put("path", "native-acme4j");
        data.put("version", "acme4j");
        data.put("message", "已启用内置 ACME 客户端，无需安装 acme.sh");
        return data;
    }

    public Map<String, Object> installAcmeSh() {
        return acmeStatus();
    }

    private SslCertificateIssueResult issueNative(SslCertificate certificate) {
        DnsProvider provider = DnsProvider.of(certificate.getDnsProvider());
        DnsChallengeService dnsService = switch (provider) {
            case TENCENT -> tencentDnsChallengeService;
            default -> throw new BadRequestException("内置 ACME 暂只支持腾讯云 DNS");
        };
        SslCertificateIssueResult result = nativeAcmeClient.issue(certificate, dnsService);
        saveToTarget(certificate.getStoragePath(), result.getCertPem(), result.getPrivateKeyPem(), result.getFullchainPem(), result.getFullchainPem());
        result.setLastMessage(result.getLastMessage() + "，并保存到 " + certificate.getStoragePath());
        return result;
    }

    private void saveToTarget(String storagePath, String certPem, String privateKeyPem, String fullchainPem, String caPem) {
        FileUtil.createDirectories(storagePath);
        writeTarget(storagePath + "/fullchain.pem", fullchainPem);
        writeTarget(storagePath + "/privkey.pem", privateKeyPem);
        writeTarget(storagePath + "/cert.pem", certPem);
        writeTarget(storagePath + "/ca.cer", caPem);
    }

    private void writeTarget(String target, String content) {
        try {
            FileUtil.writeFile(target, content);
        } catch (RuntimeException e) {
            throw new BadRequestException("写入证书文件失败: " + target + ", " + e.getMessage());
        }
    }

    private void applyIssueResult(SslCertificate certificate, SslCertificateIssueResult result) {
        certificate.setCertificateInfo(result.getCertificateInfo());
        certificate.setIssuedAt(result.getIssuedAt());
        certificate.setExpiresAt(result.getExpiresAt());
        certificate.setStatus(SslCertificateStatus.ISSUED);
        certificate.setLastMessage(result.getLastMessage());
    }

    private void validate(SslCertificateRequest request) {
        if (request == null) throw new BadRequestException("请求参数不能为空");
        if (StringUtils.hasText(request.getIssuer()) && !ISSUER.equals(request.getIssuer())) {
            throw new BadRequestException("暂只支持 Let's Encrypt");
        }
        if (!StringUtils.hasText(request.getAccountName())) throw new BadRequestException("账户名称不能为空");
        validateAccountEmail(request.getAccountName());
        if (!StringUtils.hasText(request.getDomains())) throw new BadRequestException("绑定域名不能为空");
        if (!StringUtils.hasText(request.getStoragePath())) throw new BadRequestException("存储位置不能为空");
        DnsProvider provider = DnsProvider.of(request.getDnsProvider());
        provider.validate(request);
    }

    private void validateAccountEmail(String email) {
        String value = email.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BadRequestException("账户名称需填写有效邮箱，用于注册 Let's Encrypt ACME 账户");
        }
        if (value.endsWith("@example.com") || value.endsWith("@example.org") || value.endsWith("@example.net")) {
            throw new BadRequestException("Let's Encrypt 不接受 example.com/example.org/example.net 测试邮箱，请填写真实邮箱");
        }
    }

    private String normalizeDomains(String domains) {
        return String.join(", ", parseDomains(domains));
    }

    private String resolveStoragePath(String basePath, String domains) {
        String normalizedBase = basePath.trim();
        while (normalizedBase.endsWith("/") || normalizedBase.endsWith("\\")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        String primaryDomain = parseDomains(domains).get(0);
        String directoryName = primaryDomain.replace('*', '_') + "_" + Instant.now().getEpochSecond();
        return normalizedBase + "/" + directoryName;
    }

    private List<String> parseDomains(String domains) {
        List<String> result = Arrays.stream(domains.split("[,\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (result.isEmpty()) {
            throw new BadRequestException("绑定域名不能为空");
        }
        return result;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private enum DnsProvider {
        ALIYUN("aliyun"),
        CLOUDFLARE("cloudflare"),
        TENCENT("tencent");

        private final String value;

        DnsProvider(String value) {
            this.value = value;
        }

        static DnsProvider of(String value) {
            if (!StringUtils.hasText(value)) {
                throw new BadRequestException("DNS 服务商不能为空");
            }
            return Arrays.stream(values())
                    .filter(provider -> provider.value.equalsIgnoreCase(value.trim()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("暂不支持该 DNS 服务商: " + value));
        }

        void validate(SslCertificateRequest request) {
            if (this == CLOUDFLARE) {
                if (!StringUtils.hasText(request.getDnsCredentialToken())) {
                    throw new BadRequestException("Cloudflare API Token 不能为空");
                }
                return;
            }
            if (!StringUtils.hasText(request.getDnsCredentialId()) || !StringUtils.hasText(request.getDnsCredentialSecret())) {
                throw new BadRequestException("DNS AccessKey ID 和 Secret 不能为空");
            }
        }
    }
}
