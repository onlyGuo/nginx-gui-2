package ink.icoding.nginx.cert;

import ink.icoding.nginx.core.BadRequestException;

/**
 * Parses an ACME DNS-01 rrName (e.g. "_acme-challenge.www.example.com.")
 * into a root domain and subdomain/RR name suitable for DNS provider APIs.
 */
record DnsRecordName(String domain, String subDomain) {

    static DnsRecordName parse(String rrName, String domains) {
        String root = rootDomain(domains);
        String suffix = "." + root;
        String normalized = rrName.endsWith(".")
                ? rrName.substring(0, rrName.length() - 1)
                : rrName;
        if (!normalized.endsWith(suffix)) {
            throw new BadRequestException("DNS 验证记录不属于绑定域名: " + rrName);
        }
        String sub = normalized.substring(0, normalized.length() - suffix.length());
        if (sub.isBlank()) {
            sub = "@";
        }
        return new DnsRecordName(root, sub);
    }

    private static String rootDomain(String domains) {
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
}
