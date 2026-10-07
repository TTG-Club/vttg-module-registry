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

    /** Перечитывает {@code module.json} по сохранённой ссылке: новая версия, новый архив. */
    public SubmissionResponse refreshManifest(UUID id, UUID userId) {
        String manifestUrl = readOnly.execute(status -> requireOwned(id, userId).getManifestUrl());
        ModuleManifest manifest = manifestFetcher.fetch(URI.create(manifestUrl));

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

    public Page<SubmissionResponse> findForModeration(SubmissionStatus status, Pageable pageable) {
        return readOnly.execute(tx -> (status == null
                ? repository.findAll(pageable)
                : repository.findByStatus(status, pageable))
                .map(SubmissionResponse::from));
    }

    public SubmissionResponse approve(UUID id, UUID moderatorId, String comment) {
        return transaction.execute(status -> {
            ModuleSubmission submission = require(id);
            submission.approve(moderatorId, comment, clock.instant());
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
        URI repositoryUrl = urlPolicy.require(request.repositoryUrl(), "repositoryUrl");
        URI manifestUrl = manifestFetcher.resolveManifestUrl(request.manifestUrl());
        Set<String> systemIds = gameSystemService.requireExisting(request.systemIds());
        ModuleManifest manifest = manifestFetcher.fetch(manifestUrl);

        return new Prepared(repositoryUrl.toString(), manifestUrl.toString(), systemIds, manifest);
    }

    private void requireModuleIdFree(String moduleId, UUID authorId, UUID exceptSubmissionId) {
        repository.findLiveByModuleId(moduleId)
                .filter(live -> !live.getId().equals(exceptSubmissionId))
                .ifPresent(live -> {
                    throw new ModuleIdTakenException(live.isOwnedBy(authorId)
                            ? "У вас уже есть заявка на модуль «" + moduleId + "» — правьте её"
                            : "Модуль с id «" + moduleId + "» уже зарегистрирован другим автором");
                });
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
