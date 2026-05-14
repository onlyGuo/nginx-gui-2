package ink.icoding.nginx.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "ai_chat_session")
public class AiChatSession {

    @Id
    private Long userId;

    @Column(columnDefinition = "CLOB")
    private String sessionData;
}
