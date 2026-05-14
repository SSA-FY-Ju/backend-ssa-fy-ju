package ssafy.SSAju;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(
		info = @Info(
				title = "SSAju Career Consulting API",
				version = "1.0.0",
				description = "사주 기반 커리어 상담 서비스 API",
				contact = @Contact(
						name = "SSAju Team",
						email = "contact@ssaju.com"
				)
		),
		servers = {
				@Server(url = "http://localhost:8080", description = "로컬 개발 환경"),
				@Server(url = "https://api.ssaju.com", description = "프로덕션 환경")
		}
)
public class SsAjuApplication {

	public static void main(String[] args) {
		SpringApplication.run(SsAjuApplication.class, args);
	}

}
