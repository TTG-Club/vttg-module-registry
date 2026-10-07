package club.ttg.moduleregistry.system.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameGameSystemRequest(@NotBlank @Size(max = 150) String name) {
}
