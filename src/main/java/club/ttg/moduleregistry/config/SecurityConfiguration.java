package club.ttg.moduleregistry.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfiguration {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MODERATOR = "MODERATOR";

    private static final String[] PUBLIC_PATHS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/actuator/health"
    };

    private static final int MIN_SECRET_LENGTH_BYTES = 32;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Каталог одобренных модулей читает VTTG без учётной записи.
                        .requestMatchers(HttpMethod.GET, "/api/v1/modules", "/api/v1/modules/*").permitAll()
                        // Справочник систем нужен форме заявки ещё до входа.
                        .requestMatchers(HttpMethod.GET, "/api/v1/systems").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole(ROLE_ADMIN)
                        .requestMatchers("/api/v1/moderation/**").hasAnyRole(ROLE_ADMIN, ROLE_MODERATOR)
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration site = new CorsConfiguration();
        site.setAllowedOrigins(properties.allowedOrigins());
        site.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        site.setAllowedHeaders(List.of("*"));

        // Каталог открыт любому источнику: клиент VTTG работает и из браузера,
        // и из Electron, а данных пользователя в каталоге нет.
        CorsConfiguration catalog = new CorsConfiguration();
        catalog.addAllowedOriginPattern("*");
        catalog.setAllowedMethods(List.of("GET"));
        catalog.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/modules", catalog);
        source.registerCorsConfiguration("/api/v1/modules/*", catalog);
        source.registerCorsConfiguration("/api/v1/**", site);
        return source;
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${auth-service.jwt-secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "auth-service.jwt-secret is too short. Current length: " + keyBytes.length
                            + " bytes. Minimum required length for HS256 is " + MIN_SECRET_LENGTH_BYTES + " bytes."
            );
        }

        MacAlgorithm algorithm = resolveMacAlgorithm(keyBytes.length);
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(keyBytes, jcaAlgorithmName(algorithm)))
                .macAlgorithm(algorithm)
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                SecurityConfiguration::validateSubject
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /** Модератор ли автор токена: ему открыты чужие заявки. */
    public static boolean isModerator(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && (roles.contains(ROLE_ADMIN) || roles.contains(ROLE_MODERATOR));
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfiguration::extractRoles);
        return converter;
    }

    private static Collection<GrantedAuthority> extractRoles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    private static OAuth2TokenValidatorResult validateSubject(Jwt jwt) {
        try {
            UUID.fromString(jwt.getSubject());
            return OAuth2TokenValidatorResult.success();
        } catch (IllegalArgumentException | NullPointerException exception) {
            OAuth2Error error = new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN,
                    "JWT subject must be a UUID",
                    null
            );
            return OAuth2TokenValidatorResult.failure(error);
        }
    }

    private static MacAlgorithm resolveMacAlgorithm(int secretLengthBytes) {
        int secretLengthBits = secretLengthBytes * 8;
        if (secretLengthBits >= 512) {
            return MacAlgorithm.HS512;
        }
        if (secretLengthBits >= 384) {
            return MacAlgorithm.HS384;
        }
        return MacAlgorithm.HS256;
    }

    private static String jcaAlgorithmName(MacAlgorithm algorithm) {
        if (MacAlgorithm.HS512.equals(algorithm)) {
            return "HmacSHA512";
        }
        if (MacAlgorithm.HS384.equals(algorithm)) {
            return "HmacSHA384";
        }
        return "HmacSHA256";
    }
}
