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
 * Любое, кроме WITHDRAWN ──withdraw──▶ WITHDRAWN (конец)
 * </pre>
 */
public enum SubmissionStatus {
    /** Ждёт модератора. */
    PENDING,
    /** Модуль в каталоге, VTTG его видит. */
    APPROVED,
    /** Отклонена или снята из каталога; комментарий обязателен. */
    REJECTED,
    /** Отозвана автором. */
    WITHDRAWN;

    /** Живая заявка держит id модуля: второй такой же подать нельзя. */
    public boolean isLive() {
        return this == PENDING || this == APPROVED;
    }
}
