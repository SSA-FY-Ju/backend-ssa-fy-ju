package ssafy.SSAju.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        // TODO: Phase 3.2 (ConsultationService) - Verify and enhance JSON Mode configuration
        // JSON Mode is configured via application.yaml properties:
        // spring.ai.openai.chat.options.response-format.type: JSON_OBJECT
        // Validation and error handling for JSON parsing to be added during
        // ConsultationService implementation and integration testing
        return builder.build();
    }
}
