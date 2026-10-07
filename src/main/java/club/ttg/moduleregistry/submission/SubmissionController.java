package club.ttg.moduleregistry.submission;

import club.ttg.moduleregistry.config.SecurityConfiguration;
import club.ttg.moduleregistry.submission.api.SubmissionRequest;
import club.ttg.moduleregistry.submission.api.SubmissionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Заявки автора модуля. */
@RestController
@RequestMapping("/api/v1/submissions")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Заявки авторов")
public class SubmissionController {

    private final SubmissionService service;

    public SubmissionController(SubmissionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Подать заявку на регистрацию модуля",
            description = "Сервис скачивает module.json по manifestUrl и проверяет его; "
                    + "в манифесте обязательны id, name, version и download")
    public SubmissionResponse submit(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SubmissionRequest request
    ) {
        return service.submit(userId(jwt), jwt.getClaimAsString("username"), request);
    }

    @GetMapping("/my")
    @Operation(summary = "Мои заявки, новые сверху")
    public List<SubmissionResponse> findMine(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return service.findMine(userId(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Заявка: автору — своя, модератору — любая")
    public SubmissionResponse findById(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return service.findVisible(id, userId(jwt), SecurityConfiguration.isModerator(jwt));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Исправить заявку и отправить на повторное рассмотрение",
            description = "Только заявка на рассмотрении или отклонённая")
    public SubmissionResponse resubmit(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody SubmissionRequest request
    ) {
        return service.resubmit(id, userId(jwt), request);
    }

    @PostMapping("/{id}/refresh-manifest")
    @Operation(summary = "Перечитать module.json",
            description = "Обновляет версию и ссылку на архив без повторной модерации; id модуля менять нельзя")
    public SubmissionResponse refreshManifest(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return service.refreshManifest(id, userId(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Отозвать заявку; одобренный модуль пропадёт из каталога")
    public void withdraw(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        service.withdraw(id, userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
