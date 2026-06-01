package ink.icoding.nginx.cert;

public interface DnsChallengeService {
    String createTxtRecord(SslCertificate certificate, String rrName, String value);
    void deleteRecord(SslCertificate certificate, String rrName, String recordId);
}
