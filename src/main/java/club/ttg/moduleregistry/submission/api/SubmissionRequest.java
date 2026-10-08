package club.ttg.moduleregistry.submission.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Заявка автора. Репозиторий отдельно не указывается: сервис берёт его из
 * ссылки на манифест. Описание и игровые системы — тоже из манифеста
 * ({@code description}, {@code compatibleSystems}): автор ведёт их в одном
 * месте, и каталог показывает то же, что увидит мастер в VTTG.
 *
 * @param manifestUrl ссылка на {@code module.json} в открытом репозитории
 *                    GitHub или GitLab; страницу файла на GitHub сервис сам
 *                    заменит на «сырой» файл
 */
public record SubmissionRequest(
        @NotBlank @Size(max = 2048) String manifestUrl
) {
}
