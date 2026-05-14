package ink.icoding.nginx.cert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SslCertificateServiceTest {

    @Test
    void acmeStatusUsesNativeClientWithoutAcmeSh() {
        SslCertificateService service = new SslCertificateService(null, null, null);
        var status = service.acmeStatus();
        assertEquals(true, status.get("installed"));
        assertEquals("native-acme4j", status.get("path"));
        assertTrue(String.valueOf(status.get("message")).contains("内置 ACME"));
    }
}
