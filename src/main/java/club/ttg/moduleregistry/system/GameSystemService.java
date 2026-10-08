package club.ttg.moduleregistry.system;

import club.ttg.moduleregistry.system.api.GameSystemRequest;
import club.ttg.moduleregistry.system.api.GameSystemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

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
}
