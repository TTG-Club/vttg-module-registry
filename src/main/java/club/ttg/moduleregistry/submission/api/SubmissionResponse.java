package club.ttg.moduleregistry.submission.api;

import club.ttg.moduleregistry.submission.ModuleSubmission;
import club.ttg.moduleregistry.submission.SubmissionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Заявка целиком — для автора и модератора. */
public record SubmissionResponse(
        UUID id,
        SubmissionStatus status,
        UUID authorId,
        String authorName,
        String repositoryUrl,
        String manifestUrl,
        String description,
        List<String> systemIds,
        ModuleInfo module,
        Moderation moderation,
        /** Ссылки зафиксированы первым одобрением: сменить их можно только новой заявкой. */
        boolean linksLocked,
        Instant approvedAt,
        Instant createdAt,
        Instant updatedAt
) {

    /** Снимок {@code module.json}; {@code manifest} — документ как пришёл. */
    public record ModuleInfo(
            String id,
            String name,
            String version,
            String author,
            String icon,
            String downloadUrl,
            String manifest,
            Instant syncedAt
    ) {
    }

    public record Moderation(UUID moderatorId, String comment, Instant reviewedAt) {
    }

    public static SubmissionResponse from(ModuleSubmission submission) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getStatus(),
                submission.getAuthorId(),
                submission.getAuthorName(),
                submission.getRepositoryUrl(),
                submission.getManifestUrl(),
                submission.getDescription(),
                submission.getSystemIds().stream().sorted().toList(),
                new ModuleInfo(
                        submission.getModuleId(),
                        submission.getModuleName(),
                        submission.getModuleVersion(),
                        submission.getModuleAuthor(),
                        submission.getModuleIcon(),
                        submission.getDownloadUrl(),
                        submission.getManifestJson(),
                        submission.getManifestSyncedAt()
                ),
                submission.getReviewedAt() == null ? null : new Moderation(
                        submission.getModeratorId(),
                        submission.getModerationComment(),
                        submission.getReviewedAt()
                ),
                submission.isLinksLocked(),
                submission.getApprovedAt(),
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }
}
