package club.ttg.moduleregistry.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    OpenAPI moduleRegistryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("VTTG Module Registry API")
                        .description("Регистрация, модерация и каталог модулей виртуального стола VTTG")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(
                        SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT-токен доступа auth-service. Формат: `Bearer <token>`")
                ));
    }
}
