package ink.icoding.nginx.config;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "ai_config")
public class AiConfig {

    @Id
    private Long id = 1L;

    private String baseUrl = "";

    private String apiKey = "";

    private String modelName = "";

    private String protocol = "OpenAI";
}
