package ink.icoding.nginx.cert;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SslCertificateSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        relaxNullable("ISSUED_AT");
        relaxNullable("EXPIRES_AT");
    }

    private void relaxNullable(String column) {
        try {
            jdbcTemplate.execute("ALTER TABLE ssl_certificate ALTER COLUMN " + column + " DROP NOT NULL");
        } catch (Exception ignored) {
        }
    }
}
