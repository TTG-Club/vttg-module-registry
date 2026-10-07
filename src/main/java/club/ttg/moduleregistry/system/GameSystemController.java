package club.ttg.moduleregistry.system;

import club.ttg.moduleregistry.system.api.GameSystemRequest;
import club.ttg.moduleregistry.system.api.GameSystemResponse;
import club.ttg.moduleregistry.system.api.RenameGameSystemRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Справочник игровых систем: читают все, правит администратор. */
@RestController
@Tag(name = "Игровые системы")
public class GameSystemController {

    private final GameSystemService service;

    public GameSystemController(GameSystemService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/systems")
    @Operation(summary = "Список игровых систем для формы заявки")
    public List<GameSystemResponse> findAll() {
        return service.findAll();
    }

    @PostMapping("/api/v1/admin/systems")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Добавить игровую систему")
    public GameSystemResponse create(@Valid @RequestBody GameSystemRequest request) {
        return service.create(request);
    }

    @PatchMapping("/api/v1/admin/systems/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Переименовать игровую систему")
    public GameSystemResponse rename(@PathVariable String id, @Valid @RequestBody RenameGameSystemRequest request) {
        return service.rename(id, request.name());
    }

    @DeleteMapping("/api/v1/admin/systems/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Удалить игровую систему, если её не указали ни в одной заявке")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
