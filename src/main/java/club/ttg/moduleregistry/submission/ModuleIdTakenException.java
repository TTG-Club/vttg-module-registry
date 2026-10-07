package club.ttg.moduleregistry.submission;

/** У id модуля уже есть живая заявка — своя или чужая. */
public class ModuleIdTakenException extends RuntimeException {

    public ModuleIdTakenException(String message) {
        super(message);
    }
}
