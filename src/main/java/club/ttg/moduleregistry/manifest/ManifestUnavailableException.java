package club.ttg.moduleregistry.manifest;

/** Манифест не скачался: хост недоступен, таймаут, не тот ответ. */
public class ManifestUnavailableException extends RuntimeException {

    public ManifestUnavailableException(String message) {
        super(message);
    }
}
