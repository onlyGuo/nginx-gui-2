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
import java.util.Hashtable;
import java.util.List;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

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
        String recordId = dnsService.createTxtRecord(certificate, rrName, challenge.getDigest());
        try {
            waitForDnsPropagation(rrName, challenge.getDigest());
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

    /**
     * Polls DNS for the _acme-challenge TXT record to verify propagation.
     * Minimum wait 20s, poll interval 5s, max timeout 60s after initial wait.
     */
    private void waitForDnsPropagation(String rrName, String expectedValue) throws InterruptedException {
        long timeoutMs = 60_000L;
        Thread.sleep(20_000L);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (checkTxtRecord(rrName, expectedValue)) {
                return;
            }
            Thread.sleep(5_000L);
        }
    }

    private boolean checkTxtRecord(String rrName, String expectedValue) {
        try {
            String normalized = rrName.endsWith(".") ? rrName : rrName + ".";
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", "dns://8.8.8.8");
            DirContext ctx = new InitialDirContext(env);
            try {
                Attributes attrs = ctx.getAttributes(normalized, new String[]{"TXT"});
                Attribute txtAttr = attrs.get("TXT");
                if (txtAttr != null) {
                    for (int i = 0; i < txtAttr.size(); i++) {
                        String txtValue = txtAttr.get(i).toString().replace("\"", "").trim();
                        if (txtValue.equals(expectedValue)) {
                            return true;
                        }
                    }
                }
            } finally {
                ctx.close();
            }
        } catch (javax.naming.NamingException ignored) {
        }
        return false;
    }
}
