package club.ttg.moduleregistry.catalog;

import club.ttg.moduleregistry.catalog.api.CatalogModuleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Каталог одобренных модулей — его читает VTTG. */
@RestController
@RequestMapping("/api/v1/modules")
@Tag(name = "Каталог модулей")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Одобренные модули",
            description = "С параметром system — только подходящие миру на этой системе, включая универсальные")
    public List<CatalogModuleResponse> find(
            @Parameter(description = "id игровой системы мира, например dnd5e-2024")
            @RequestParam(required = false) String system
    ) {
        return service.find(system);
    }

    @GetMapping("/{moduleId}")
    @Operation(summary = "Одобренный модуль по id из module.json")
    public CatalogModuleResponse findById(@PathVariable String moduleId) {
        return service.findById(moduleId);
    }
}
