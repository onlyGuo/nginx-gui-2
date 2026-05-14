package ink.icoding.nginx.cert;

public interface DnsChallengeService {
    Long createTxtRecord(SslCertificate certificate, String rrName, String value);
    void deleteRecord(SslCertificate certificate, String rrName, Long recordId);
}
