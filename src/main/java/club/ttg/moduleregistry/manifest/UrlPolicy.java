package club.ttg.moduleregistry.manifest;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Какие ссылки сервис принимает от авторов модулей.
 *
 * Ссылки уходят и в наши исходящие запросы (манифест), и в VTTG (архив),
 * поэтому пускаем только https на хосты из списка — так в запрос не
 * попадут ни внутренние адреса, ни произвольные сайты.
 */
@Component
public class UrlPolicy {

    /** {@code https://github.com/<owner>/<repo>/blob/<ref>/<path>} — страница файла, а не сам файл. */
    private static final Pattern GITHUB_BLOB =
            Pattern.compile("^/([^/]+)/([^/]+)/blob/(.+)$");

    private final ManifestProperties properties;

    public UrlPolicy(ManifestProperties properties) {
        this.properties = properties;
    }

    /** Проверяет ссылку и возвращает её в разобранном виде. */
    public URI require(String raw, String field) {
        URI uri;
        try {
            uri = new URI(raw.strip());
        } catch (URISyntaxException | NullPointerException exception) {
            throw new InvalidManifestException(field + ": некорректная ссылка");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new InvalidManifestException(field + ": нужна ссылка https://");
        }
        if (uri.getRawUserInfo() != null) {
            throw new InvalidManifestException(field + ": логин и пароль в ссылке недопустимы");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new InvalidManifestException(field + ": нестандартный порт недопустим");
        }

        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        if (host == null || !properties.allowedHosts().contains(host)) {
            throw new InvalidManifestException(field + ": хост не из списка разрешённых ("
                    + String.join(", ", properties.allowedHosts()) + ")");
        }

        return uri.normalize();
    }

    /**
     * Ссылку на страницу файла в GitHub меняет на сам файл: авторы чаще
     * копируют адрес из браузера, а по нему отдаётся HTML, а не JSON.
     */
    public URI toRawFile(URI uri) {
        if (!"github.com".equalsIgnoreCase(uri.getHost())) {
            return uri;
        }

        Matcher matcher = GITHUB_BLOB.matcher(uri.getRawPath());
        if (!matcher.matches()) {
            return uri;
        }

        return URI.create("https://raw.githubusercontent.com/"
                + matcher.group(1) + "/" + matcher.group(2) + "/" + matcher.group(3));
    }

    /**
     * Нормализованная ссылка на репозиторий: без хвостового «/» и «.git».
     * По ней сравниваются ссылки заявок, поэтому вид должен быть один.
     */
    public URI requireRepository(String raw) {
        URI uri = require(raw, "repositoryUrl");
        String path = repositoryPath(uri);
        // Минимум «/владелец/репозиторий»: у GitLab групп может быть больше.
        boolean hasOwnerAndName = path.split("/").length >= 3;
        if (!hasOwnerAndName || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new InvalidManifestException("repositoryUrl: нужна ссылка на сам репозиторий");
        }
        return URI.create("https://" + uri.getHost().toLowerCase(Locale.ROOT) + path);
    }

    /**
     * Проверяет, что файл лежит в заявленном репозитории: тот же хост (для
     * GitHub — ещё и raw.githubusercontent.com) и путь внутри репозитория.
     * Иначе одобренная ссылка на репозиторий ничего бы не значила: манифест
     * и архив могли бы вести куда угодно.
     */
    public void requireInsideRepository(URI repository, URI file, String field) {
        String fileHost = file.getHost() == null ? "" : file.getHost().toLowerCase(Locale.ROOT);
        String repositoryHost = repository.getHost().toLowerCase(Locale.ROOT);
        boolean sameHost = fileHost.equals(repositoryHost)
                || ("github.com".equals(repositoryHost) && "raw.githubusercontent.com".equals(fileHost));

        String prefix = repositoryPath(repository) + "/";
        String filePath = file.normalize().getRawPath();

        if (!sameHost || filePath == null || !filePath.startsWith(prefix) || filePath.contains("/../")) {
            throw new InvalidManifestException(
                    field + ": файл должен лежать в репозитории " + repository);
        }
    }

    private static String repositoryPath(URI uri) {
        String path = uri.normalize().getRawPath() == null ? "" : uri.normalize().getRawPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - ".git".length());
        }
        return path;
    }
}
