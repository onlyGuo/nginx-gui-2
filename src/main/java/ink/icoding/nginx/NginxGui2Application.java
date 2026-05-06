package ink.icoding.nginx;

import ink.icoding.nginx.config.PathConfig;
import ink.icoding.nginx.config.PathConfigRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NginxGui2Application {

    private static final Long CONFIG_ID = 1L;

    private final PathConfigRepository pathConfigRepository;

    public NginxGui2Application(PathConfigRepository pathConfigRepository) {
        this.pathConfigRepository = pathConfigRepository;
    }

    public static void main(String[] args) {
        SpringApplication.run(NginxGui2Application.class, args);
    }

    @PostConstruct
    public void initConfig() {
        if (pathConfigRepository.findById(CONFIG_ID).isEmpty()) {
            PathConfig defaults = new PathConfig();
            defaults.setId(CONFIG_ID);
            pathConfigRepository.save(defaults);
        }
    }
}
