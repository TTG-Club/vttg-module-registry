package club.ttg.moduleregistry.submission;

import club.ttg.moduleregistry.manifest.InvalidManifestException;
import club.ttg.moduleregistry.manifest.ManifestFetcher;
import club.ttg.moduleregistry.manifest.ModuleManifest;
import club.ttg.moduleregistry.manifest.UrlPolicy;
import club.ttg.moduleregistry.manifest.UrlPolicyTest;
import club.ttg.moduleregistry.submission.api.SubmissionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SubmissionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID MODERATOR = UUID.randomUUID();
    private static final String REPOSITORY = "https://github.com/a/b";
    private static final String MANIFEST = "https://raw.githubusercontent.com/a/b/main/module.json";

    private final ModuleSubmissionRepository repository = mock(ModuleSubmissionRepository.class);
    private final ManifestFetcher manifestFetcher = mock(ManifestFetcher.class);
    private SubmissionService service;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());

        service = new SubmissionService(
                repository,
                manifestFetcher,
                new UrlPolicy(UrlPolicyTest.properties()),
                new TransactionTemplate(transactionManager),
                Clock.fixed(NOW, ZoneOffset.UTC));

        given(manifestFetcher.resolveManifestUrl(any())).willAnswer(call -> URI.create(call.getArgument(0)));
        given(repository.save(any())).willAnswer(call -> call.getArgument(0));
    }

    @Test
    void authorCanSubmitReplacementForOwnApprovedModule() {
        ModuleSubmission approved = approved(REPOSITORY, MANIFEST);
        given(repository.findLiveByModuleId("m")).willReturn(List.of(approved));
        given(manifestFetcher.fetch(any())).willReturn(manifest("https://github.com/a/new/releases/download/v1/m.zip"));

        var response = service.submit(AUTHOR, "author",
                request("https://raw.githubusercontent.com/a/new/main/module.json"));

        assertThat(response.status()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(response.repositoryUrl()).isEqualTo("https://github.com/a/new");
    }

    @Test
    void secondPendingSubmissionIsRefused() {
        ModuleSubmission pending = pending(REPOSITORY, MANIFEST);
        given(repository.findLiveByModuleId("m")).willReturn(List.of(pending));
        given(manifestFetcher.fetch(any())).willReturn(manifest("https://github.com/a/b/releases/download/v1/m.zip"));

        assertThatThrownBy(() -> service.submit(AUTHOR, "author", request(MANIFEST)))
                .isInstanceOf(ModuleIdTakenException.class)
                .hasMessageContaining("на рассмотрении");
    }

    @Test
    void foreignModuleIdIsRefused() {
        ModuleSubmission foreign = ModuleSubmission.submit(UUID.randomUUID(), "other", REPOSITORY, MANIFEST,
                "x", manifest("https://github.com/a/b/releases/download/v1/m.zip"), NOW);
        given(repository.findLiveByModuleId("m")).willReturn(List.of(foreign));
        given(manifestFetcher.fetch(any())).willReturn(manifest("https://github.com/a/b/releases/download/v1/m.zip"));

        assertThatThrownBy(() -> service.submit(AUTHOR, "author", request(MANIFEST)))
                .isInstanceOf(ModuleIdTakenException.class)
                .hasMessageContaining("другим автором");
    }

    @Test
    void archiveMustLiveInManifestRepository() {
        given(repository.findLiveByModuleId("m")).willReturn(List.of());

        // Из такой ссылки не понять, в каком репозитории лежит манифест.
        assertThatThrownBy(() -> service.submit(AUTHOR, "author", request("https://github.com/a")))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("manifestUrl");

        given(manifestFetcher.fetch(any())).willReturn(manifest("https://github.com/evil/x/releases/download/v1/m.zip"));
        assertThatThrownBy(() -> service.submit(AUTHOR, "author", request(MANIFEST)))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("download");
        verify(repository, never()).save(any());
    }

    @Test
    void refreshCannotMoveArchiveOutOfRepository() {
        ModuleSubmission approved = approved(REPOSITORY, MANIFEST);
        given(repository.findById(approved.getId())).willReturn(Optional.of(approved));
        given(manifestFetcher.fetch(any())).willReturn(manifest("https://github.com/evil/x/releases/download/v2/m.zip"));

        assertThatThrownBy(() -> service.refreshManifest(approved.getId(), AUTHOR))
                .isInstanceOf(InvalidManifestException.class);
    }

    @Test
    void approvingReplacementSupersedesPreviousApproval() {
        ModuleSubmission previous = approved(REPOSITORY, MANIFEST);
        ModuleSubmission replacement = pending("https://github.com/a/new",
                "https://raw.githubusercontent.com/a/new/main/module.json");
        given(repository.findById(replacement.getId())).willReturn(Optional.of(replacement));
        given(repository.findApprovedByModuleId("m")).willReturn(Optional.of(previous));

        var response = service.approve(replacement.getId(), MODERATOR, null);

        assertThat(response.status()).isEqualTo(SubmissionStatus.APPROVED);
        assertThat(response.linksLocked()).isTrue();
        assertThat(previous.getStatus()).isEqualTo(SubmissionStatus.SUPERSEDED);
        verify(repository).saveAndFlush(previous);
    }

    private static SubmissionRequest request(String manifestUrl) {
        return new SubmissionRequest(manifestUrl, "Описание");
    }

    private static ModuleManifest manifest(String download) {
        return new ModuleManifest("m", "Модуль", "1.0.0", null, null, download, List.of(), "{}");
    }

    private static ModuleSubmission pending(String repositoryUrl, String manifestUrl) {
        return ModuleSubmission.submit(AUTHOR, "author", repositoryUrl, manifestUrl, "x",
                manifest(repositoryUrl + "/releases/download/v1/m.zip"), NOW);
    }

    private static ModuleSubmission approved(String repositoryUrl, String manifestUrl) {
        ModuleSubmission submission = pending(repositoryUrl, manifestUrl);
        submission.approve(MODERATOR, null, NOW);
        return submission;
    }
}
