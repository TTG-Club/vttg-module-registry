package club.ttg.moduleregistry.submission.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Заявка автора. Репозиторий отдельно не указывается: сервис берёт его из
 * ссылки на манифест. Игровые системы тоже — из {@code compatibleSystems}
 * манифеста: по нему же совместимость проверяет сам VTTG.
 *
 * @param manifestUrl ссылка на {@code module.json} в открытом репозитории
 *                    GitHub или GitLab; страницу файла на GitHub сервис сам
 *                    заменит на «сырой» файл
 */
public record SubmissionRequest(
        @NotBlank @Size(max = 2048) String manifestUrl,
        @NotBlank @Size(max = 1000) String description
) {
}
