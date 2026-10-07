package club.ttg.moduleregistry.system;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GameSystemRepository extends JpaRepository<GameSystem, String> {

    List<GameSystem> findAllByOrderByNameAsc();

    @Query(value = "select exists(select 1 from module_submission_systems where system_id = :id)",
            nativeQuery = true)
    boolean isUsedBySubmissions(String id);
}
