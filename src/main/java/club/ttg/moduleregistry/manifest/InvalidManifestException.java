package club.ttg.moduleregistry.manifest;

/** Манифест или ссылка не прошли проверку — ошибка автора, а не сети. */
public class InvalidManifestException extends RuntimeException {

    public InvalidManifestException(String message) {
        super(message);
    }
}
