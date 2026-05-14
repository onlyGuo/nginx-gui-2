package ink.icoding.nginx.cert;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.dnspod.v20210323.DnspodClient;
import com.tencentcloudapi.dnspod.v20210323.models.CreateRecordRequest;
import com.tencentcloudapi.dnspod.v20210323.models.CreateRecordResponse;
import com.tencentcloudapi.dnspod.v20210323.models.DeleteRecordRequest;
import ink.icoding.nginx.core.BadRequestException;
import org.springframework.stereotype.Component;

@Component
public class TencentDnsChallengeService implements DnsChallengeService {

    @Override
    public Long createTxtRecord(SslCertificate certificate, String rrName, String value) {
        TencentRecordName name = parseRecordName(rrName, certificate.getDomains());
        try {
            CreateRecordRequest request = new CreateRecordRequest();
            request.setDomain(name.domain());
            request.setSubDomain(name.subDomain());
            request.setRecordType("TXT");
            request.setRecordLine("默认");
            request.setValue(value);
            CreateRecordResponse response = client(certificate).CreateRecord(request);
            return response.getRecordId();
        } catch (TencentCloudSDKException e) {
            throw new BadRequestException("创建腾讯云 DNS TXT 记录失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteRecord(SslCertificate certificate, String rrName, Long recordId) {
        if (recordId == null) {
            return;
        }
        TencentRecordName name = parseRecordName(rrName, certificate.getDomains());
        try {
            DeleteRecordRequest request = new DeleteRecordRequest();
            request.setDomain(name.domain());
            request.setRecordId(recordId);
            client(certificate).DeleteRecord(request);
        } catch (TencentCloudSDKException ignored) {
        }
    }

    private DnspodClient client(SslCertificate certificate) {
        Credential credential = new Credential(certificate.getDnsCredentialId(), certificate.getDnsCredentialSecret(), emptyToNull(certificate.getDnsCredentialToken()));
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("dnspod.tencentcloudapi.com");
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        return new DnspodClient(credential, "ap-guangzhou", clientProfile);
    }

    private TencentRecordName parseRecordName(String rrName, String domains) {
        String domain = rootDomain(domains);
        String suffix = "." + domain;
        String normalized = rrName.endsWith(".") ? rrName.substring(0, rrName.length() - 1) : rrName;
        if (!normalized.endsWith(suffix)) {
            throw new BadRequestException("DNS 验证记录不属于绑定域名: " + rrName);
        }
        String subDomain = normalized.substring(0, normalized.length() - suffix.length());
        if (subDomain.isBlank()) {
            subDomain = "@";
        }
        return new TencentRecordName(domain, subDomain);
    }

    private String rootDomain(String domains) {
        String domain = domains.split("[,\\s]+")[0].trim();
        if (domain.startsWith("*.")) {
            domain = domain.substring(2);
        }
        String[] parts = domain.split("\\.");
        if (parts.length < 2) {
            throw new BadRequestException("域名格式不正确: " + domain);
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record TencentRecordName(String domain, String subDomain) {
    }
}
