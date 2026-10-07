package club.ttg.moduleregistry.submission;

/** Действие недопустимо в текущем состоянии заявки. */
public class InvalidSubmissionStateException extends RuntimeException {

    public InvalidSubmissionStateException(String message) {
        super(message);
    }
}
