package club.ttg.moduleregistry.submission;

/**
 * Состояние заявки.
 *
 * <pre>
 * PENDING ──approve──▶ APPROVED ──reject (снятие)──▶ REJECTED
 *    │                    │                              │
 *    └──reject──▶ REJECTED ◀──────────────────────────────┘
 *                    │
 *                    └──правка автором──▶ PENDING
 *
 * APPROVED ──одобрена новая заявка автора на тот же модуль──▶ SUPERSEDED (конец)
 * Любое, кроме WITHDRAWN и SUPERSEDED ──withdraw──▶ WITHDRAWN (конец)
 * </pre>
 *
 * Ссылки на репозиторий и манифест фиксируются при первом одобрении: чтобы
 * сменить их, автор подаёт новую заявку на тот же модуль, и она после
 * одобрения заменяет прежнюю в каталоге.
 */
public enum SubmissionStatus {
    /** Ждёт модератора. */
    PENDING,
    /** Модуль в каталоге, VTTG его видит. */
    APPROVED,
    /** Отклонена или снята из каталога; комментарий обязателен. */
    REJECTED,
    /** Отозвана автором. */
    WITHDRAWN,
    /** Заменена новой одобренной заявкой автора на тот же модуль. */
    SUPERSEDED;

    /** Живая заявка держит id модуля от других авторов. */
    public boolean isLive() {
        return this == PENDING || this == APPROVED;
    }

    /** Заявка завершена и больше не меняется. */
    public boolean isFinal() {
        return this == WITHDRAWN || this == SUPERSEDED;
    }
}
