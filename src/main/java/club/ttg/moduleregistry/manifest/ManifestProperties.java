package club.ttg.moduleregistry.manifest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Где и как сервис читает {@code module.json}.
 *
 * @param allowedHosts хосты, на которые могут указывать ссылки на репозиторий,
 *                     манифест и архив модуля (точное совпадение, без поддоменов)
 * @param maxSize      предельный размер манифеста
 * @param timeout      таймаут соединения и чтения
 * @param maxRedirects сколько переадресаций пройти; каждая проверяется заново
 */
@ConfigurationProperties(prefix = "manifest")
public record ManifestProperties(
        List<String> allowedHosts,
        DataSize maxSize,
        Duration timeout,
        Integer maxRedirects
) {

    public ManifestProperties {
        allowedHosts = allowedHosts == null ? List.of() : allowedHosts.stream()
                .map(String::strip)
                .filter(host -> !host.isEmpty())
                .map(host -> host.toLowerCase(Locale.ROOT))
                .toList();
        maxSize = maxSize == null ? DataSize.ofKilobytes(256) : maxSize;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        maxRedirects = maxRedirects == null ? 3 : maxRedirects;
    }
}
