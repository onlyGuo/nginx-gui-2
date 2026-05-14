package ink.icoding.nginx.web;

import ink.icoding.nginx.cert.SslCertificate;
import ink.icoding.nginx.cert.SslCertificateRepository;
import ink.icoding.nginx.cert.SslCertificateRequest;
import ink.icoding.nginx.cert.SslCertificateService;
import ink.icoding.nginx.core.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/certificates")
@RequiredArgsConstructor
public class SslCertificateController {

    private final SslCertificateRepository repository;
    private final SslCertificateService certificateService;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<SslCertificate> result = repository.findAll(
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", result.getContent());
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("total", result.getTotalElements());
        data.put("totalPages", result.getTotalPages());
        return ApiResponse.ok(data);
    }

    @PostMapping
    public ApiResponse<SslCertificate> create(@RequestBody SslCertificateRequest request) {
        return ApiResponse.ok(certificateService.issue(request));
    }

    @PutMapping("/{id}/auto-renew")
    public ApiResponse<SslCertificate> updateAutoRenew(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return ApiResponse.ok(certificateService.updateAutoRenew(id, Boolean.TRUE.equals(body.get("autoRenew"))));
    }

    @PostMapping("/{id}/renew")
    public ApiResponse<SslCertificate> renew(@PathVariable Long id) {
        SslCertificate certificate = repository.findById(id)
                .orElseThrow(() -> new BadRequestException("证书不存在"));
        return ApiResponse.ok(certificateService.renew(certificate));
    }

    @GetMapping("/acme/status")
    public ApiResponse<Map<String, Object>> acmeStatus() {
        return ApiResponse.ok(certificateService.acmeStatus());
    }

    @PostMapping("/acme/install")
    public ApiResponse<Map<String, Object>> installAcme() {
        return ApiResponse.ok(certificateService.installAcmeSh());
    }
}
