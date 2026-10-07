package club.ttg.moduleregistry.submission;

import club.ttg.moduleregistry.manifest.ManifestFetcher;
import club.ttg.moduleregistry.manifest.ModuleManifest;
import club.ttg.moduleregistry.manifest.UrlPolicy;
import club.ttg.moduleregistry.submission.api.SubmissionRequest;
import club.ttg.moduleregistry.submission.api.SubmissionResponse;
import club.ttg.moduleregistry.system.GameSystemService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Заявки авторов и решения модераторов.
 *
 * Манифест скачивается вне транзакции: чужой сервер может отвечать до
 * таймаута, и держать всё это время соединение с базой незачем. Поэтому
 * методы с загрузкой проверяют заявку дважды — до загрузки и в транзакции
 * записи; гонку правок ловит {@code @Version}.
 */
@Service
public class SubmissionService {

    private final ModuleSubmissionRepository repository;
    private final GameSystemService gameSystemService;
    private final ManifestFetcher manifestFetcher;
    private final UrlPolicy urlPolicy;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    public SubmissionService(
            ModuleSubmissionRepository repository,
            GameSystemService gameSystemService,
            ManifestFetcher manifestFetcher,
            UrlPolicy urlPolicy,
            TransactionTemplate transaction,
            Clock clock
    ) {
        this.repository = repository;
        this.gameSystemService = gameSystemService;
        this.manifestFetcher = manifestFetcher;
        this.urlPolicy = urlPolicy;
        this.transaction = transaction;
        this.readOnly = new TransactionTemplate(transaction.getTransactionManager());
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    public SubmissionResponse submit(UUID authorId, String authorName, SubmissionRequest request) {
        Prepared prepared = prepare(request);

        return transaction.execute(status -> {
            requireModuleIdFree(prepared.manifest().id(), authorId, null);

            ModuleSubmission submission = ModuleSubmission.submit(
                    authorId,
                    authorName,
                    prepared.repositoryUrl(),
                    prepared.manifestUrl(),
                    request.description(),
                    prepared.systemIds(),
                    prepared.manifest(),
                    clock.instant()
            );
            return SubmissionResponse.from(repository.save(submission));
        });
    }

    public SubmissionResponse resubmit(UUID id, UUID userId, SubmissionRequest request) {
        readOnly.executeWithoutResult(status -> requireOwned(id, userId));
        Prepared prepared = prepare(request);

        return transaction.execute(status -> {
            ModuleSubmission submission = requireOwned(id, userId);
            requireModuleIdFree(prepared.manifest().id(), userId, id);

            submission.resubmit(
                    prepared.repositoryUrl(),
                    prepared.manifestUrl(),
                    request.description(),
                    prepared.systemIds(),
                    prepared.manifest(),
                    clock.instant()
            );
            return SubmissionResponse.from(submission);
        });
    }

    /**
     * Перечитывает {@code module.json} по сохранённой ссылке: новая версия и
     * архив без повторной модерации. Архив по-прежнему обязан лежать в том же
     * репозитории, так что сменить источник модуля так нельзя.
     */
    public SubmissionResponse refreshManifest(UUID id, UUID userId) {
        ModuleSubmission stored = readOnly.execute(status -> requireOwned(id, userId));
        ModuleManifest manifest = fetchFromRepository(
                URI.create(stored.getRepositoryUrl()), URI.create(stored.getManifestUrl()));

        return transaction.execute(status -> {
            ModuleSubmission submission = requireOwned(id, userId);
            submission.refreshManifest(manifest, clock.instant());
            return SubmissionResponse.from(submission);
        });
    }

    public void withdraw(UUID id, UUID userId) {
        transaction.executeWithoutResult(status -> requireOwned(id, userId).withdraw(clock.instant()));
    }

    public List<SubmissionResponse> findMine(UUID userId) {
        return readOnly.execute(status -> repository.findByAuthorIdOrderByCreatedAtDesc(userId).stream()
                .map(SubmissionResponse::from)
                .toList());
    }

    /** Автор видит свою заявку, модератор — любую; остальным её как будто нет. */
    public SubmissionResponse findVisible(UUID id, UUID userId, boolean moderator) {
        return readOnly.execute(status -> {
            ModuleSubmission submission = repository.findById(id)
                    .filter(found -> moderator || found.isOwnedBy(userId))
                    .orElseThrow(() -> new SubmissionNotFoundException(id));
            return SubmissionResponse.from(submission);
        });
    }

    /** Очередь модерации: заявки с любым из статусов; пустой список — все заявки. */
    public Page<SubmissionResponse> findForModeration(Collection<SubmissionStatus> statuses, Pageable pageable) {
        return readOnly.execute(tx -> (statuses == null || statuses.isEmpty()
                ? repository.findAll(pageable)
                : repository.findByStatusIn(statuses, pageable))
                .map(SubmissionResponse::from));
    }

    /**
     * Одобряет заявку. Если у модуля уже есть одобренная заявка того же
     * автора, новая её заменяет: прежняя уходит из каталога со статусом
     * {@link SubmissionStatus#SUPERSEDED}.
     */
    public SubmissionResponse approve(UUID id, UUID moderatorId, String comment) {
        return transaction.execute(status -> {
            ModuleSubmission submission = require(id);
            Instant now = clock.instant();

            repository.findApprovedByModuleId(submission.getModuleId())
                    .filter(approved -> !approved.getId().equals(id))
                    .ifPresent(approved -> {
                        if (!approved.isOwnedBy(submission.getAuthorId())) {
                            throw new ModuleIdTakenException("Модуль с id «" + submission.getModuleId()
                                    + "» уже зарегистрирован другим автором");
                        }
                        approved.supersede(now);
                        // Прежняя должна уйти из одобренных до того, как одобрим
                        // новую: иначе сработает уникальный индекс по id модуля.
                        repository.saveAndFlush(approved);
                    });

            submission.approve(moderatorId, comment, now);
            return SubmissionResponse.from(submission);
        });
    }

    public SubmissionResponse reject(UUID id, UUID moderatorId, String comment) {
        return transaction.execute(status -> {
            ModuleSubmission submission = require(id);
            submission.reject(moderatorId, comment, clock.instant());
            return SubmissionResponse.from(submission);
        });
    }

    /** Проверки без базы и загрузка манифеста — до транзакции. */
    private Prepared prepare(SubmissionRequest request) {
        URI manifestUrl = manifestFetcher.resolveManifestUrl(request.manifestUrl());
        // Репозиторий не спрашиваем у автора, а берём из ссылки на манифест.
        URI repositoryUrl = urlPolicy.repositoryOf(manifestUrl, "manifestUrl");
        Set<String> systemIds = gameSystemService.requireExisting(request.systemIds());
        ModuleManifest manifest = fetchFromRepository(repositoryUrl, manifestUrl);

        return new Prepared(repositoryUrl.toString(), manifestUrl.toString(), systemIds, manifest);
    }

    /** Манифест и архив модуля должны лежать в заявленном репозитории. */
    private ModuleManifest fetchFromRepository(URI repositoryUrl, URI manifestUrl) {
        ModuleManifest manifest = manifestFetcher.fetch(manifestUrl);
        urlPolicy.requireInsideRepository(repositoryUrl, URI.create(manifest.download()), "download");
        return manifest;
    }

    /**
     * Чужой модуль занять нельзя. Своему одобренному модулю автор может
     * подать одну заявку-замену (например, со сменой ссылок), но вторую
     * заявку на рассмотрении — нет: её нужно править.
     */
    private void requireModuleIdFree(String moduleId, UUID authorId, UUID exceptSubmissionId) {
        for (ModuleSubmission live : repository.findLiveByModuleId(moduleId)) {
            if (live.getId().equals(exceptSubmissionId)) {
                continue;
            }
            if (!live.isOwnedBy(authorId)) {
                throw new ModuleIdTakenException(
                        "Модуль с id «" + moduleId + "» уже зарегистрирован другим автором");
            }
            if (live.getStatus() == SubmissionStatus.PENDING) {
                throw new ModuleIdTakenException(
                        "У вас уже есть заявка на модуль «" + moduleId + "» на рассмотрении — правьте её");
            }
        }
    }

    private ModuleSubmission requireOwned(UUID id, UUID userId) {
        return repository.findById(id)
                .filter(submission -> submission.isOwnedBy(userId))
                .orElseThrow(() -> new SubmissionNotFoundException(id));
    }

    private ModuleSubmission require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new SubmissionNotFoundException(id));
    }

    private record Prepared(String repositoryUrl, String manifestUrl, Set<String> systemIds, ModuleManifest manifest) {
    }
}
