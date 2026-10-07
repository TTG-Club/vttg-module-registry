package club.ttg.moduleregistry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Сайты, с которых core-app ходит в API: подача заявок и модерация. */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
