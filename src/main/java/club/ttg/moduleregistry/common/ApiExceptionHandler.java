package club.ttg.moduleregistry.common;

import club.ttg.moduleregistry.catalog.CatalogModuleNotFoundException;
import club.ttg.moduleregistry.manifest.InvalidManifestException;
import club.ttg.moduleregistry.manifest.ManifestUnavailableException;
import club.ttg.moduleregistry.submission.InvalidSubmissionStateException;
import club.ttg.moduleregistry.submission.ModuleIdTakenException;
import club.ttg.moduleregistry.submission.SubmissionNotFoundException;
import club.ttg.moduleregistry.system.GameSystemAlreadyExistsException;
import club.ttg.moduleregistry.system.GameSystemInUseException;
import club.ttg.moduleregistry.system.GameSystemNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(SubmissionNotFoundException.class)
    ProblemDetail handleSubmissionNotFound(SubmissionNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Заявка не найдена", exception.getMessage());
    }

    @ExceptionHandler(CatalogModuleNotFoundException.class)
    ProblemDetail handleCatalogModuleNotFound(CatalogModuleNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Модуль не найден", exception.getMessage());
    }

    @ExceptionHandler(InvalidManifestException.class)
    ProblemDetail handleInvalidManifest(InvalidManifestException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Манифест модуля не прошёл проверку", exception.getMessage());
    }

    @ExceptionHandler(ManifestUnavailableException.class)
    ProblemDetail handleManifestUnavailable(ManifestUnavailableException exception) {
        return problem(HttpStatus.BAD_GATEWAY, "Манифест модуля недоступен", exception.getMessage());
    }

    @ExceptionHandler(ModuleIdTakenException.class)
    ProblemDetail handleModuleIdTaken(ModuleIdTakenException exception) {
        return problem(HttpStatus.CONFLICT, "Модуль уже заявлен", exception.getMessage());
    }

    @ExceptionHandler(InvalidSubmissionStateException.class)
    ProblemDetail handleInvalidSubmissionState(InvalidSubmissionStateException exception) {
        return problem(HttpStatus.CONFLICT, "Действие недоступно", exception.getMessage());
    }

    @ExceptionHandler(GameSystemNotFoundException.class)
    ProblemDetail handleGameSystemNotFound(GameSystemNotFoundException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Игровая система не найдена", exception.getMessage());
    }

    @ExceptionHandler(GameSystemAlreadyExistsException.class)
    ProblemDetail handleGameSystemExists(GameSystemAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, "Игровая система уже есть", exception.getMessage());
    }

    @ExceptionHandler(GameSystemInUseException.class)
    ProblemDetail handleGameSystemInUse(GameSystemInUseException exception) {
        return problem(HttpStatus.CONFLICT, "Игровая система используется", exception.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "Заявку изменили одновременно с вами",
                "Обновите страницу и повторите действие");
    }

    /** Чаще всего — гонка двух заявок на один id модуля мимо проверки в сервисе. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("Нарушено ограничение хранилища", exception);
        return problem(HttpStatus.CONFLICT, "Запись отклонена хранилищем", constraintOf(exception));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Ошибка валидации", "Проверьте поля запроса");
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Ошибка валидации", exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        log.warn("Тело запроса не разобрано: {}", exception.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Запрос не разобран",
                "Не удалось прочитать запрос. Обновите страницу и попробуйте ещё раз");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, PropertyReferenceException.class})
    ProblemDetail handleBadParameter(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Некорректный параметр запроса", exception.getMessage());
    }

    private static String constraintOf(DataIntegrityViolationException exception) {
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null) {
                return "Нарушено ограничение " + violation.getConstraintName();
            }
            cause = cause.getCause();
        }
        return "Данные не прошли проверку хранилища";
    }

    private ProblemDetail problem(HttpStatus status, String title, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(title);
        detail.setType(URI.create("https://ttg.club/problems/vttg-modules/" + status.value()));
        return detail;
    }
}
