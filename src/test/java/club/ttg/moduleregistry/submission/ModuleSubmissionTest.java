package club.ttg.moduleregistry.submission;

import club.ttg.moduleregistry.manifest.ModuleManifest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleSubmissionTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID MODERATOR = UUID.randomUUID();

    static ModuleManifest manifest(String id, String version) {
        return new ModuleManifest(id, "Модуль", version, null, null,
                "https://github.com/a/b/releases/download/v" + version + "/m.zip", List.of(), "{}");
    }

    static ModuleSubmission pending() {
        return ModuleSubmission.submit(AUTHOR, "author", "https://github.com/a/b",
                "https://raw.githubusercontent.com/a/b/main/module.json", " Описание ",
                List.of("dnd5e-2024"), manifest("m", "1.0.0"), NOW);
    }

    @Test
    void newSubmissionIsPending() {
        ModuleSubmission submission = pending();

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(submission.getDescription()).isEqualTo("Описание");
        assertThat(submission.getSystemIds()).containsExactly("dnd5e-2024");
        assertThat(submission.isOwnedBy(AUTHOR)).isTrue();
    }

    @Test
    void approvesPending() {
        ModuleSubmission submission = pending();

        submission.approve(MODERATOR, "  ", NOW);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.APPROVED);
        assertThat(submission.getModeratorId()).isEqualTo(MODERATOR);
        assertThat(submission.getModerationComment()).isNull();
        assertThat(submission.getReviewedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectRequiresComment() {
        ModuleSubmission submission = pending();

        assertThatThrownBy(() -> submission.reject(MODERATOR, " ", NOW))
                .isInstanceOf(InvalidSubmissionStateException.class);

        submission.reject(MODERATOR, "Нет README", NOW);
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.REJECTED);
        assertThat(submission.getModerationComment()).isEqualTo("Нет README");
    }

    @Test
    void approvedModuleCanBeTakenDownButNotReapproved() {
        ModuleSubmission submission = pending();
        submission.approve(MODERATOR, null, NOW);

        submission.reject(MODERATOR, "Нарушает правила", NOW);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.REJECTED);
        assertThatThrownBy(() -> submission.approve(MODERATOR, null, NOW))
                .isInstanceOf(InvalidSubmissionStateException.class);
    }

    @Test
    void resubmitAfterRejectionReturnsToQueueAndClearsDecision() {
        ModuleSubmission submission = pending();
        submission.reject(MODERATOR, "Нет README", NOW);

        submission.resubmit("https://github.com/a/b", "https://raw.githubusercontent.com/a/b/main/module.json",
                "Новое описание", List.of(), manifest("m", "1.0.1"), NOW);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(submission.getModerationComment()).isNull();
        assertThat(submission.getReviewedAt()).isNull();
        assertThat(submission.getSystemIds()).isEmpty();
        assertThat(submission.getModuleVersion()).isEqualTo("1.0.1");
    }

    @Test
    void approvedSubmissionCannotBeEdited() {
        ModuleSubmission submission = pending();
        submission.approve(MODERATOR, null, NOW);

        assertThatThrownBy(() -> submission.resubmit("https://github.com/a/b",
                "https://raw.githubusercontent.com/a/b/main/module.json", "x", List.of(),
                manifest("m", "1.0.1"), NOW))
                .isInstanceOf(InvalidSubmissionStateException.class);
    }

    @Test
    void refreshKeepsStatusButRefusesIdChange() {
        ModuleSubmission submission = pending();
        submission.approve(MODERATOR, null, NOW);

        submission.refreshManifest(manifest("m", "2.0.0"), NOW);
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.APPROVED);
        assertThat(submission.getModuleVersion()).isEqualTo("2.0.0");

        assertThatThrownBy(() -> submission.refreshManifest(manifest("other", "2.0.0"), NOW))
                .isInstanceOf(InvalidSubmissionStateException.class);
    }

    @Test
    void withdrawIsFinal() {
        ModuleSubmission submission = pending();

        submission.withdraw(NOW);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.WITHDRAWN);
        assertThat(submission.getStatus().isLive()).isFalse();
        assertThatThrownBy(() -> submission.withdraw(NOW)).isInstanceOf(InvalidSubmissionStateException.class);
        assertThatThrownBy(() -> submission.approve(MODERATOR, null, NOW))
                .isInstanceOf(InvalidSubmissionStateException.class);
    }
}
