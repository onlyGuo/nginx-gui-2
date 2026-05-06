package ink.icoding.nginx.config;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "path_config")
public class PathConfig {

    @Id
    private Long id = 1L;

    private String nginxBin = "/usr/sbin/nginx";
    private String nginxConf = "/etc/nginx/nginx.conf";
    private String confDir = "";

}
