package ink.icoding.nginx.cert;

import com.aliyun.alidns20150109.Client;
import com.aliyun.alidns20150109.models.AddDomainRecordRequest;
import com.aliyun.alidns20150109.models.AddDomainRecordResponse;
import com.aliyun.alidns20150109.models.DeleteDomainRecordRequest;
import com.aliyun.teaopenapi.models.Config;
import ink.icoding.nginx.core.BadRequestException;
import org.springframework.stereotype.Component;

@Component
public class AliyunDnsChallengeService implements DnsChallengeService {

    @Override
    public String createTxtRecord(SslCertificate certificate, String rrName, String value) {
        DnsRecordName name = DnsRecordName.parse(rrName, certificate.getDomains());
        try {
            AddDomainRecordRequest request = new AddDomainRecordRequest();
            request.setDomainName(name.domain());
            request.setRR(name.subDomain());
            request.setType("TXT");
            request.setValue(value);
            AddDomainRecordResponse response = client(certificate).addDomainRecord(request);
            return response.getBody().getRecordId();
        } catch (Exception e) {
            throw new BadRequestException("创建阿里云 DNS TXT 记录失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteRecord(SslCertificate certificate, String rrName, String recordId) {
        if (recordId == null) {
            return;
        }
        try {
            DeleteDomainRecordRequest request = new DeleteDomainRecordRequest();
            request.setRecordId(recordId);
            client(certificate).deleteDomainRecord(request);
        } catch (Exception ignored) {
        }
    }

    private Client client(SslCertificate certificate) {
        try {
            Config config = new Config()
                    .setAccessKeyId(certificate.getDnsCredentialId())
                    .setAccessKeySecret(certificate.getDnsCredentialSecret())
                    .setEndpoint("alidns.aliyuncs.com");
            return new Client(config);
        } catch (Exception e) {
            throw new BadRequestException("初始化阿里云 DNS 客户端失败: " + e.getMessage());
        }
    }
}
