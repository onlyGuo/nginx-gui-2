package ink.icoding.nginx.cert;

import lombok.Data;

@Data
public class SslCertificateRequest {
    private String issuer;
    private String accountName;
    private String remark;
    private String domains;
    private String storagePath;
    private Boolean autoRenew;
    private String dnsProvider;
    private String dnsCredentialId;
    private String dnsCredentialSecret;
    private String dnsCredentialToken;
}
