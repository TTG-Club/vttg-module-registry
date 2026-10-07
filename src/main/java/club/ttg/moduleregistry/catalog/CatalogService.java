package club.ttg.moduleregistry.catalog;

import club.ttg.moduleregistry.catalog.api.CatalogModuleResponse;
import club.ttg.moduleregistry.submission.ModuleSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogService {

    private final ModuleSubmissionRepository repository;

    public CatalogService(ModuleSubmissionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CatalogModuleResponse> find(String systemId) {
        var modules = systemId == null || systemId.isBlank()
                ? repository.findApproved()
                : repository.findApprovedForSystem(systemId.strip());
        return modules.stream().map(CatalogModuleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CatalogModuleResponse findById(String moduleId) {
        return repository.findApprovedByModuleId(moduleId)
                .map(CatalogModuleResponse::from)
                .orElseThrow(() -> new CatalogModuleNotFoundException(moduleId));
    }
}
