package club.ttg.moduleregistry.submission;

import club.ttg.moduleregistry.manifest.ModuleManifest;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Заявка автора на включение модуля в каталог VTTG. */
@Entity
@Table(name = "module_submissions")
public class ModuleSubmission {

    @Id
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "author_name", length = 100)
    private String authorName;

    @Column(name = "repository_url", nullable = false, length = 2048)
    private String repositoryUrl;

    @Column(name = "manifest_url", nullable = false, length = 2048)
    private String manifestUrl;

    @Column(nullable = false, length = 1000)
    private String description;

    @ElementCollection
    @CollectionTable(name = "module_submission_systems", joinColumns = @JoinColumn(name = "submission_id"))
    @Column(name = "system_id", nullable = false, length = 64)
    private Set<String> systemIds = new LinkedHashSet<>();

    @Column(name = "module_id", nullable = false, length = 64)
    private String moduleId;

    @Column(name = "module_name", nullable = false, length = 150)
    private String moduleName;

    @Column(name = "module_version", nullable = false, length = 50)
    private String moduleVersion;

    @Column(name = "module_author", length = 150)
    private String moduleAuthor;

    @Column(name = "module_icon", length = 100)
    private String moduleIcon;

    @Column(name = "download_url", nullable = false, length = 2048)
    private String downloadUrl;

    @Column(name = "manifest_json", nullable = false, columnDefinition = "text")
    private String manifestJson;

    @Column(name = "manifest_synced_at", nullable = false)
    private Instant manifestSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @Column(name = "moderator_id")
    private UUID moderatorId;

    @Column(name = "moderation_comment", length = 2000)
    private String moderationComment;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** Первое одобрение; с него ссылки заявки больше не меняются. */
    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ModuleSubmission() {
    }

    public static ModuleSubmission submit(
            UUID authorId,
            String authorName,
            String repositoryUrl,
            String manifestUrl,
            String description,
            Collection<String> systemIds,
            ModuleManifest manifest,
            Instant now
    ) {
        ModuleSubmission submission = new ModuleSubmission();
        submission.id = UUID.randomUUID();
        submission.authorId = authorId;
        submission.authorName = authorName;
        submission.status = SubmissionStatus.PENDING;
        submission.createdAt = now;
        submission.setDetails(repositoryUrl, manifestUrl, description, systemIds);
        submission.applyManifest(manifest, now);
        return submission;
    }

    /** Правка автором: заявка уходит на повторное рассмотрение. */
    public void resubmit(
            String repositoryUrl,
            String manifestUrl,
            String description,
            Collection<String> systemIds,
            ModuleManifest manifest,
            Instant now
    ) {
        if (status != SubmissionStatus.PENDING && status != SubmissionStatus.REJECTED) {
            throw new InvalidSubmissionStateException(
                    "Править можно только заявку на рассмотрении или отклонённую");
        }
        if (isLinksLocked()) {
            if (!this.repositoryUrl.equals(repositoryUrl) || !this.manifestUrl.equals(manifestUrl)) {
                throw new InvalidSubmissionStateException(
                        "Ссылку на module.json одобренного модуля менять нельзя. Чтобы сменить её, подайте новую заявку");
            }
            if (!manifest.id().equals(moduleId)) {
                throw new InvalidSubmissionStateException(
                        "В манифесте сменился id модуля (" + moduleId + " → " + manifest.id()
                                + "). Это другой модуль — подайте на него отдельную заявку");
            }
        }
        setDetails(repositoryUrl, manifestUrl, description, systemIds);
        applyManifest(manifest, now);
        status = SubmissionStatus.PENDING;
        moderatorId = null;
        moderationComment = null;
        reviewedAt = null;
    }

    /**
     * Обновляет снимок манифеста без смены статуса: новая версия модуля
     * не требует повторной модерации, а id модуля менять нельзя.
     */
    public void refreshManifest(ModuleManifest manifest, Instant now) {
        if (status.isFinal()) {
            throw new InvalidSubmissionStateException("Заявка закрыта");
        }
        if (!manifest.id().equals(moduleId)) {
            throw new InvalidSubmissionStateException(
                    "В манифесте сменился id модуля (" + moduleId + " → " + manifest.id()
                            + "). Это другой модуль — подайте на него отдельную заявку");
        }
        applyManifest(manifest, now);
    }

    public void approve(UUID moderatorId, String comment, Instant now) {
        if (status != SubmissionStatus.PENDING) {
            throw new InvalidSubmissionStateException("Одобрить можно только заявку на рассмотрении");
        }
        decide(SubmissionStatus.APPROVED, moderatorId, comment, now);
        if (approvedAt == null) {
            approvedAt = now;
        }
    }

    /** Одобрена новая заявка автора на этот же модуль — эта уходит из каталога. */
    public void supersede(Instant now) {
        if (status != SubmissionStatus.APPROVED) {
            throw new InvalidSubmissionStateException("Заменить можно только одобренную заявку");
        }
        status = SubmissionStatus.SUPERSEDED;
        updatedAt = now;
    }

    /**
     * Ссылки на репозиторий и манифест фиксируются при первом одобрении и
     * дальше не меняются — даже если модуль сняли из каталога.
     */
    public boolean isLinksLocked() {
        return approvedAt != null;
    }

    /** Отклоняет заявку на рассмотрении или снимает модуль из каталога. */
    public void reject(UUID moderatorId, String comment, Instant now) {
        if (status != SubmissionStatus.PENDING && status != SubmissionStatus.APPROVED) {
            throw new InvalidSubmissionStateException(
                    "Отклонить можно заявку на рассмотрении или одобренную");
        }
        if (comment == null || comment.isBlank()) {
            throw new InvalidSubmissionStateException("Укажите причину отклонения");
        }
        decide(SubmissionStatus.REJECTED, moderatorId, comment, now);
    }

    public void withdraw(Instant now) {
        if (status.isFinal()) {
            throw new InvalidSubmissionStateException("Заявка уже закрыта");
        }
        status = SubmissionStatus.WITHDRAWN;
        updatedAt = now;
    }

    public boolean isOwnedBy(UUID userId) {
        return authorId.equals(userId);
    }

    private void decide(SubmissionStatus next, UUID moderatorId, String comment, Instant now) {
        this.status = next;
        this.moderatorId = moderatorId;
        this.moderationComment = comment == null || comment.isBlank() ? null : comment.strip();
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    private void setDetails(String repositoryUrl, String manifestUrl, String description, Collection<String> systemIds) {
        this.repositoryUrl = repositoryUrl;
        this.manifestUrl = manifestUrl;
        this.description = description.strip();
        this.systemIds.clear();
        this.systemIds.addAll(systemIds);
    }

    private void applyManifest(ModuleManifest manifest, Instant now) {
        this.moduleId = manifest.id();
        this.moduleName = manifest.name();
        this.moduleVersion = manifest.version();
        this.moduleAuthor = manifest.author();
        this.moduleIcon = manifest.icon();
        this.downloadUrl = manifest.download();
        this.manifestJson = manifest.json();
        this.manifestSyncedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getManifestUrl() {
        return manifestUrl;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getSystemIds() {
        return Set.copyOf(systemIds);
    }

    public String getModuleId() {
        return moduleId;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getModuleVersion() {
        return moduleVersion;
    }

    public String getModuleAuthor() {
        return moduleAuthor;
    }

    public String getModuleIcon() {
        return moduleIcon;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getManifestJson() {
        return manifestJson;
    }

    public Instant getManifestSyncedAt() {
        return manifestSyncedAt;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public UUID getModeratorId() {
        return moderatorId;
    }

    public String getModerationComment() {
        return moderationComment;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
