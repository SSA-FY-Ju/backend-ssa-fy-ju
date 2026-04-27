package ssafy.SSAju.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        // TODO: Phase 2 - Configure JSON Mode for OpenAI API responses
        // JSON Mode is currently configured via application.yaml properties:
        // spring.ai.openai.chat.options.response-format.type: JSON_OBJECT
        // Validation and error handling for JSON parsing should be added during
        // ConsultationService implementation
        return builder.build();
    }
}
