package ink.icoding.nginx.cert;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SslCertificateRepository extends JpaRepository<SslCertificate, Long> {
}
