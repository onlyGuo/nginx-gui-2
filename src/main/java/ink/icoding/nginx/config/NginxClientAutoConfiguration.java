package ink.icoding.nginx.config;

import ink.icoding.nginx.core.NginxClient;
import ink.icoding.nginx.core.NginxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class NginxClientAutoConfiguration {

    private static final Long CONFIG_ID = 1L;

    @Bean
    public NginxClient nginxClient(PathConfigRepository repository) {
        NginxClient client = new NginxClient();

        PathConfig config = repository.findById(CONFIG_ID).orElse(null);
        if (config == null) {
            log.info("路径配置不存在，NginxClient 未初始化，等待用户配置");
            return client;
        }

        try {
            client.reinit(config.getNginxBin(), config.getNginxConf(), config.getConfDir());
            log.info("NginxClient 初始化成功: bin={}, conf={}", config.getNginxBin(), config.getNginxConf());
        } catch (NginxException e) {
            log.warn("NginxClient 初始化失败（路径无效），等待用户修正: {}", e.getMessage());
        }

        return client;
    }
}
