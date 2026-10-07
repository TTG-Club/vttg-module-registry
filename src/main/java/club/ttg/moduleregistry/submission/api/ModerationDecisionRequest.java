package club.ttg.moduleregistry.submission.api;

import jakarta.validation.constraints.Size;

/** Комментарий модератора: при одобрении необязателен, при отклонении обязателен. */
public record ModerationDecisionRequest(@Size(max = 2000) String comment) {
}
