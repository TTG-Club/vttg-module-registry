package club.ttg.moduleregistry.submission;

import club.ttg.moduleregistry.submission.api.ModerationDecisionRequest;
import club.ttg.moduleregistry.submission.api.SubmissionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Очередь модерации: администратор одобряет или отклоняет заявки. */
@RestController
@RequestMapping("/api/v1/moderation/submissions")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Модерация")
public class SubmissionModerationController {

    private final SubmissionService service;

    public SubmissionModerationController(SubmissionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Заявки по статусу; без статуса — все. Старые сверху")
    public Page<SubmissionResponse> find(
            @RequestParam(required = false) SubmissionStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return service.findForModeration(status, pageable);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Одобрить заявку — модуль появится в каталоге VTTG")
    public SubmissionResponse approve(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ModerationDecisionRequest request
    ) {
        return service.approve(id, UUID.fromString(jwt.getSubject()), request == null ? null : request.comment());
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Отклонить заявку или снять модуль из каталога; комментарий обязателен")
    public SubmissionResponse reject(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ModerationDecisionRequest request
    ) {
        return service.reject(id, UUID.fromString(jwt.getSubject()), request.comment());
    }
}
