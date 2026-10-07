package club.ttg.moduleregistry.manifest;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Скачивает {@code module.json} по ссылке автора.
 *
 * Переадресации проходим сами, чтобы каждую проверить {@link UrlPolicy}:
 * иначе разрешённый хост мог бы увести запрос куда угодно. Тело читаем не
 * больше {@link ManifestProperties#maxSize()} — манифест маленький, а
 * чужой сервер может отдать что угодно.
 */
@Component
public class ManifestFetcher {

    private final ManifestProperties properties;
    private final UrlPolicy urlPolicy;
    private final ManifestParser parser;
    private final HttpClient httpClient;

    public ManifestFetcher(ManifestProperties properties, UrlPolicy urlPolicy, ManifestParser parser) {
        this.properties = properties;
        this.urlPolicy = urlPolicy;
        this.parser = parser;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Нормализованная ссылка на манифест — её и сохраняем в заявке. */
    public URI resolveManifestUrl(String manifestUrl) {
        return urlPolicy.toRawFile(urlPolicy.require(manifestUrl, "manifestUrl"));
    }

    public ModuleManifest fetch(URI manifestUrl) {
        return parser.parse(download(manifestUrl));
    }

    private String download(URI start) {
        URI current = start;
        for (int hop = 0; hop <= properties.maxRedirects(); hop++) {
            current = urlPolicy.require(current.toString(), "manifestUrl");
            requirePublicAddress(current);

            HttpResponse<InputStream> response = send(current);
            int status = response.statusCode();

            if (status >= 300 && status < 400) {
                closeQuietly(response.body());
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new ManifestUnavailableException(
                                "Переадресация без адреса при загрузке module.json"));
                current = current.resolve(location);
                continue;
            }

            if (status != 200) {
                closeQuietly(response.body());
                throw new ManifestUnavailableException(
                        "module.json не загружен: сервер ответил " + status);
            }

            return readLimited(response.body());
        }

        throw new ManifestUnavailableException("Слишком много переадресаций при загрузке module.json");
    }

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.timeout())
                .header("Accept", "application/json, text/plain")
                .header("User-Agent", "vttg-module-registry")
                .GET()
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException exception) {
            throw new ManifestUnavailableException("module.json не загружен: " + exception.getClass().getSimpleName());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ManifestUnavailableException("Загрузка module.json прервана");
        }
    }

    private String readLimited(InputStream body) {
        long limit = properties.maxSize().toBytes();
        try (body) {
            byte[] bytes = body.readNBytes((int) limit + 1);
            if (bytes.length > limit) {
                throw new InvalidManifestException("module.json больше " + limit / 1024 + " КБ");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ManifestUnavailableException("module.json не дочитан: " + exception.getClass().getSimpleName());
        }
    }

    /** Разрешённое имя всё равно не должно вести во внутреннюю сеть. */
    private static void requirePublicAddress(URI uri) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException exception) {
            throw new ManifestUnavailableException("Хост " + uri.getHost() + " не найден");
        }

        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()
                    || isUniqueLocalIpv6(address)) {
                throw new InvalidManifestException("manifestUrl: адрес во внутренней сети недопустим");
            }
        }
    }

    /** fc00::/7 — частные IPv6-адреса; {@code isSiteLocalAddress} их не узнаёт. */
    private static boolean isUniqueLocalIpv6(InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Тело ответа не нужно — достаточно освободить соединение.
        }
    }
}
