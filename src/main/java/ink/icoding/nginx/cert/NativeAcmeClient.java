package ink.icoding.nginx.cert;

import ink.icoding.nginx.core.BadRequestException;
import org.shredzone.acme4j.*;
import org.shredzone.acme4j.challenge.Dns01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class NativeAcmeClient {

    private static final String LETS_ENCRYPT = "acme://letsencrypt.org";
    private static final Duration CHALLENGE_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration ORDER_TIMEOUT = Duration.ofMinutes(5);

    public SslCertificateIssueResult issue(SslCertificate certificate, DnsChallengeService dnsService) {
        try {
            List<String> domains = parseDomains(certificate.getDomains());
            KeyPair accountKey = KeyPairUtils.createKeyPair(2048);
            KeyPair domainKey = KeyPairUtils.createKeyPair(2048);

            Session session = new Session(LETS_ENCRYPT);
            Account account = new AccountBuilder()
                    .agreeToTermsOfService()
                    .addEmail(certificate.getAccountName())
                    .useKeyPair(accountKey)
                    .create(session);

            Order order = account.newOrder().domains(domains).create();
            for (Authorization authorization : order.getAuthorizations()) {
                authorizeDns(authorization, certificate, dnsService);
            }

            CSRBuilder csr = new CSRBuilder();
            csr.addDomains(domains);
            csr.sign(domainKey);
            order.execute(csr.getEncoded());

            Status status = order.waitForCompletion(ORDER_TIMEOUT);
            if (status != Status.VALID) {
                throw new BadRequestException("证书签发失败，订单状态: " + status + order.getError().map(error -> ", " + error).orElse(""));
            }

            Certificate acmeCertificate = order.getCertificate();
            if (acmeCertificate == null) {
                throw new BadRequestException("证书签发成功但未返回证书内容");
            }
            acmeCertificate.download();

            String fullchain = writeCertificate(acmeCertificate);
            String privateKey = writePrivateKey(domainKey);
            X509Certificate leaf = acmeCertificate.getCertificate();
            LocalDateTime issuedAt = LocalDateTime.ofInstant(leaf.getNotBefore().toInstant(), ZoneId.systemDefault());
            LocalDateTime expiresAt = LocalDateTime.ofInstant(leaf.getNotAfter().toInstant(), ZoneId.systemDefault());
            String info = leaf.getIssuerX500Principal().getName() + " · " + leaf.getSubjectX500Principal().getName()
                    + " · " + leaf.getPublicKey().getAlgorithm() + " · " + leaf.getSigAlgName();

            return new SslCertificateIssueResult(info, issuedAt, expiresAt, "证书已通过内置 ACME 客户端签发", fullchain, privateKey, fullchain);
        } catch (AcmeException | IOException e) {
            throw new BadRequestException("申请证书失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("申请证书被中断");
        }
    }

    private void authorizeDns(Authorization authorization, SslCertificate certificate, DnsChallengeService dnsService) throws AcmeException, InterruptedException {
        if (authorization.getStatus() == Status.VALID) {
            return;
        }
        Dns01Challenge challenge = authorization.findChallenge(Dns01Challenge.class)
                .orElseThrow(() -> new BadRequestException("CA 未返回 DNS-01 验证挑战"));
        String rrName = Dns01Challenge.toRRName(authorization.getIdentifier());
        Long recordId = dnsService.createTxtRecord(certificate, rrName, challenge.getDigest());
        try {
            Thread.sleep(20_000L);
            challenge.trigger();
            Status status = challenge.waitForCompletion(CHALLENGE_TIMEOUT);
            if (status != Status.VALID) {
                throw new BadRequestException("DNS 验证失败，状态: " + status + challenge.getError().map(error -> ", " + error).orElse(""));
            }
        } finally {
            dnsService.deleteRecord(certificate, rrName, recordId);
        }
    }

    private String writeCertificate(Certificate certificate) throws IOException {
        StringWriter writer = new StringWriter();
        certificate.writeCertificate(writer);
        return writer.toString();
    }

    private String writePrivateKey(KeyPair keyPair) throws IOException {
        StringWriter writer = new StringWriter();
        KeyPairUtils.writeKeyPair(keyPair, writer);
        return writer.toString();
    }

    private List<String> parseDomains(String domains) {
        return List.of(domains.split("[,\\s]+")).stream()
                .map(String::trim)
                .filter(domain -> !domain.isBlank())
                .toList();
    }
}
