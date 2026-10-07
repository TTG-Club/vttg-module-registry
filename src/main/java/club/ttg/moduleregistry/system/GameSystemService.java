package club.ttg.moduleregistry.system;

import club.ttg.moduleregistry.system.api.GameSystemRequest;
import club.ttg.moduleregistry.system.api.GameSystemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class GameSystemService {

    private final GameSystemRepository repository;
    private final Clock clock;

    public GameSystemService(GameSystemRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GameSystemResponse> findAll() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(GameSystemResponse::from)
                .toList();
    }

    @Transactional
    public GameSystemResponse create(GameSystemRequest request) {
        if (repository.existsById(request.id())) {
            throw new GameSystemAlreadyExistsException(request.id());
        }

        GameSystem system = new GameSystem(request.id(), request.name().strip(), clock.instant());
        return GameSystemResponse.from(repository.save(system));
    }

    @Transactional
    public GameSystemResponse rename(String id, String name) {
        GameSystem system = repository.findById(id)
                .orElseThrow(() -> new GameSystemNotFoundException(id));
        system.rename(name.strip());
        return GameSystemResponse.from(system);
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new GameSystemNotFoundException(id);
        }
        if (repository.isUsedBySubmissions(id)) {
            throw new GameSystemInUseException(id);
        }
        repository.deleteById(id);
    }

    /** Проверяет, что все id есть в справочнике; порядок и дубликаты не важны. */
    @Transactional(readOnly = true)
    public Set<String> requireExisting(Collection<String> ids) {
        Set<String> unique = new LinkedHashSet<>(ids);
        Set<String> known = new LinkedHashSet<>();
        repository.findAllById(unique).forEach(system -> known.add(system.getId()));

        unique.stream()
                .filter(id -> !known.contains(id))
                .findFirst()
                .ifPresent(id -> {
                    throw new GameSystemNotFoundException(id);
                });

        return unique;
    }
}
