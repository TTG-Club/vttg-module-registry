package club.ttg.moduleregistry.submission.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Заявка автора. Репозиторий отдельно не указывается: сервис берёт его из
 * ссылки на манифест.
 *
 * @param manifestUrl ссылка на {@code module.json} в открытом репозитории
 *                    GitHub или GitLab; страницу файла на GitHub сервис сам
 *                    заменит на «сырой» файл
 * @param systemIds   id игровых систем из справочника; пустой список —
 *                    модуль универсальный
 */
public record SubmissionRequest(
        @NotBlank @Size(max = 2048) String manifestUrl,
        @NotBlank @Size(max = 1000) String description,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 64) String> systemIds
) {
}
