package ink.icoding.nginx.cert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.nginx.core.BadRequestException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class CloudflareDnsChallengeService implements DnsChallengeService {

    private static final String CF_API = "https://api.cloudflare.com/client/v4";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Override
    public String createTxtRecord(SslCertificate certificate, String rrName, String value) {
        DnsRecordName name = DnsRecordName.parse(rrName, certificate.getDomains());
        String zoneId = getZoneId(certificate, name.domain());
        try {
            String body = MAPPER.writeValueAsString(new TxtRecordBody("TXT", rrName, value, 60));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CF_API + "/zones/" + zoneId + "/dns_records"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + apiToken(certificate))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            JsonNode json = sendRequest(request);
            return json.get("result").get("id").asText();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("创建 Cloudflare DNS TXT 记录失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteRecord(SslCertificate certificate, String rrName, String recordId) {
        if (recordId == null) {
            return;
        }
        try {
            DnsRecordName name = DnsRecordName.parse(rrName, certificate.getDomains());
            String zoneId = getZoneId(certificate, name.domain());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CF_API + "/zones/" + zoneId + "/dns_records/" + recordId))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + apiToken(certificate))
                    .DELETE()
                    .build();
            sendRequest(request);
        } catch (Exception ignored) {
        }
    }

    private String getZoneId(SslCertificate certificate, String domain) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CF_API + "/zones?name=" + domain))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + apiToken(certificate))
                    .GET()
                    .build();
            JsonNode json = sendRequest(request);
            JsonNode result = json.get("result");
            if (result == null || result.isEmpty()) {
                throw new BadRequestException("Cloudflare 中未找到域名: " + domain);
            }
            return result.get(0).get("id").asText();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("查询 Cloudflare Zone 失败: " + e.getMessage());
        }
    }

    private JsonNode sendRequest(HttpRequest request) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = MAPPER.readTree(response.body());
            if (!json.has("success") || !json.get("success").asBoolean()) {
                String errors = "未知错误";
                if (json.has("errors") && json.get("errors").isArray()) {
                    List<String> msgs = new ArrayList<>();
                    for (com.fasterxml.jackson.databind.JsonNode node : json.get("errors")) {
                        msgs.add(node.has("message") ? node.get("message").asText() : node.toString());
                    }
                    errors = String.join(", ", msgs);
                }
                throw new BadRequestException("Cloudflare API 错误: " + errors);
            }
            return json;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Cloudflare API 请求失败: " + e.getMessage());
        }
    }

    private String apiToken(SslCertificate certificate) {
        return certificate.getDnsCredentialToken();
    }

    @SuppressWarnings("unused")
    private record TxtRecordBody(String type, String name, String content, int ttl) {
    }
}
