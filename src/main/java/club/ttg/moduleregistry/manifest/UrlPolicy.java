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
     * Репозиторий, в котором лежит файл, — по самой ссылке на файл:
     * <ul>
     *   <li>GitHub ({@code github.com} и {@code raw.githubusercontent.com}) —
     *       первые два сегмента пути, «владелец/репозиторий»;</li>
     *   <li>GitLab — всё до служебного {@code /-/} (группы бывают вложенными,
     *       а {@code /-/} отделяет путь репозитория и в облаке, и у своего
     *       сервера).</li>
     * </ul>
     * Автору не нужно отдельно указывать репозиторий, и указать «чужой» нельзя.
     */
    public URI repositoryOf(URI file, String field) {
        String host = file.getHost() == null ? "" : file.getHost().toLowerCase(Locale.ROOT);
        String path = file.normalize().getRawPath() == null ? "" : file.normalize().getRawPath();

        if ("github.com".equals(host) || "raw.githubusercontent.com".equals(host)) {
            String[] segments = path.split("/");
            // ["", владелец, репозиторий, ...дальше хотя бы один сегмент файла]
            if (segments.length < 4 || segments[1].isEmpty() || segments[2].isEmpty()) {
                throw new InvalidManifestException(field + ": не видно, в каком репозитории GitHub лежит файл");
            }
            return URI.create("https://github.com/" + segments[1] + "/" + stripGit(segments[2]));
        }

        int separator = path.indexOf("/-/");
        if (separator <= 0 || path.substring(0, separator).split("/").length < 3) {
            throw new InvalidManifestException(field
                    + ": не видно, в каком репозитории лежит файл. Нужна ссылка на файл в GitHub или GitLab");
        }
        return URI.create("https://" + host + stripGit(path.substring(0, separator)));
    }

    private static String stripGit(String name) {
        return name.endsWith(".git") ? name.substring(0, name.length() - ".git".length()) : name;
    }

    /**
     * Проверяет, что файл лежит в том же репозитории, что и манифест: тот же
     * хост (для GitHub — ещё и raw.githubusercontent.com) и путь внутри
     * репозитория. Иначе одобренная ссылка ничего бы не значила: архив мог бы
     * вести куда угодно.
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
