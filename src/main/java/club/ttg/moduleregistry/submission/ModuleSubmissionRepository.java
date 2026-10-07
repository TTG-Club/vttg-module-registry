package club.ttg.moduleregistry.submission;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModuleSubmissionRepository extends JpaRepository<ModuleSubmission, UUID> {

    @Query("""
            select s from ModuleSubmission s
            where s.moduleId = :moduleId
              and s.status in (club.ttg.moduleregistry.submission.SubmissionStatus.PENDING,
                               club.ttg.moduleregistry.submission.SubmissionStatus.APPROVED)
            """)
    Optional<ModuleSubmission> findLiveByModuleId(String moduleId);

    List<ModuleSubmission> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);

    Page<ModuleSubmission> findByStatus(SubmissionStatus status, Pageable pageable);

    @Query("""
            select s from ModuleSubmission s
            where s.status = club.ttg.moduleregistry.submission.SubmissionStatus.APPROVED
            order by s.moduleName
            """)
    List<ModuleSubmission> findApproved();

    /** Одобренные модули для системы мира: заявленные под неё и универсальные. */
    @Query("""
            select s from ModuleSubmission s
            where s.status = club.ttg.moduleregistry.submission.SubmissionStatus.APPROVED
              and (:systemId member of s.systemIds or s.systemIds is empty)
            order by s.moduleName
            """)
    List<ModuleSubmission> findApprovedForSystem(String systemId);

    @Query("""
            select s from ModuleSubmission s
            where s.moduleId = :moduleId
              and s.status = club.ttg.moduleregistry.submission.SubmissionStatus.APPROVED
            """)
    Optional<ModuleSubmission> findApprovedByModuleId(String moduleId);
}
