package club.ttg.moduleregistry.submission;

import java.util.UUID;

public class SubmissionNotFoundException extends RuntimeException {

    public SubmissionNotFoundException(UUID id) {
        super("Заявка " + id + " не найдена");
    }
}
