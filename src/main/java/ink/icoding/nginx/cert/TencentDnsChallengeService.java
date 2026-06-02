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
    public String createTxtRecord(SslCertificate certificate, String rrName, String value) {
        DnsRecordName name = DnsRecordName.parse(rrName, certificate.getDomains());
        try {
            CreateRecordRequest request = new CreateRecordRequest();
            request.setDomain(name.domain());
            request.setSubDomain(name.subDomain());
            request.setRecordType("TXT");
            request.setRecordLine("默认");
            request.setValue(value);
            CreateRecordResponse response = client(certificate).CreateRecord(request);
            return String.valueOf(response.getRecordId());
        } catch (TencentCloudSDKException e) {
            throw new BadRequestException("创建腾讯云 DNS TXT 记录失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteRecord(SslCertificate certificate, String rrName, String recordId) {
        if (recordId == null) {
            return;
        }
        DnsRecordName name = DnsRecordName.parse(rrName, certificate.getDomains());
        try {
            DeleteRecordRequest request = new DeleteRecordRequest();
            request.setDomain(name.domain());
            request.setRecordId(Long.parseLong(recordId));
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

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
