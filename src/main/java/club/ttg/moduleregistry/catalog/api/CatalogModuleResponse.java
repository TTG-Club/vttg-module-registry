package club.ttg.moduleregistry.catalog.api;

import club.ttg.moduleregistry.submission.ModuleSubmission;

import java.time.Instant;
import java.util.List;

/**
 * Модуль в каталоге VTTG.
 *
 * {@code version} и {@code downloadUrl} — снимок на момент последней
 * синхронизации; актуальные значения VTTG берёт из {@code manifestUrl},
 * как при обновлении систем, установленных по ссылке.
 *
 * @param systemIds пустой список — модуль подходит любому миру
 */
public record CatalogModuleResponse(
        String id,
        String name,
        String version,
        String description,
        String author,
        String icon,
        List<String> systemIds,
        String repositoryUrl,
        String manifestUrl,
        String downloadUrl,
        Instant approvedAt,
        Instant updatedAt
) {

    public static CatalogModuleResponse from(ModuleSubmission submission) {
        return new CatalogModuleResponse(
                submission.getModuleId(),
                submission.getModuleName(),
                submission.getModuleVersion(),
                submission.getDescription(),
                submission.getModuleAuthor() != null ? submission.getModuleAuthor() : submission.getAuthorName(),
                submission.getModuleIcon(),
                submission.getSystemIds().stream().sorted().toList(),
                submission.getRepositoryUrl(),
                submission.getManifestUrl(),
                submission.getDownloadUrl(),
                submission.getReviewedAt(),
                submission.getUpdatedAt()
        );
    }
}
