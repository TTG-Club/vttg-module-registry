package club.ttg.moduleregistry.system.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GameSystemRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*$",
                message = "Только строчные латинские буквы, цифры, «-» и «_» — как id в system.json")
        String id,

        @NotBlank
        @Size(max = 150)
        String name
) {
}
