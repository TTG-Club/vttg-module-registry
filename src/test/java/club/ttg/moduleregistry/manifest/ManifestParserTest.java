package club.ttg.moduleregistry.manifest;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManifestParserTest {

    private final ManifestParser parser = new ManifestParser(
            JsonMapper.builder().build(),
            new UrlPolicy(UrlPolicyTest.properties()));

    @Test
    void parsesManifestWithDownload() {
        ModuleManifest manifest = parser.parse("""
                {
                  "id": "map-import",
                  "name": "Импорт карт",
                  "version": "0.1.0",
                  "author": "TTG Club",
                  "icon": "tabler:map-plus",
                  "download": "https://github.com/ttg/map-import/releases/download/v0.1.0/module.zip",
                  "compatibleSystems": ["dnd5e-2024", "dnd5e-2024", " pf2e "]
                }
                """);

        assertThat(manifest.id()).isEqualTo("map-import");
        assertThat(manifest.name()).isEqualTo("Импорт карт");
        assertThat(manifest.version()).isEqualTo("0.1.0");
        assertThat(manifest.author()).isEqualTo("TTG Club");
        assertThat(manifest.compatibleSystems()).containsExactly("dnd5e-2024", "pf2e");
        assertThat(manifest.json()).contains("map-import");
    }

    @Test
    void acceptsTitleAsNameAlias() {
        ModuleManifest manifest = parser.parse(manifest("\"title\": \"Модуль\""));

        assertThat(manifest.name()).isEqualTo("Модуль");
    }

    @Test
    void wildcardSystemMeansUniversal() {
        ModuleManifest manifest = parser.parse(manifest("\"name\": \"М\", \"compatibleSystems\": [\"*\"]"));

        assertThat(manifest.compatibleSystems()).isEqualTo(List.of());
    }

    @Test
    void rejectsTooManySystems() {
        String systems = java.util.stream.IntStream.rangeClosed(1, 21)
                .mapToObj(index -> "\"system-" + index + "\"")
                .collect(java.util.stream.Collectors.joining(", "));

        assertThatThrownBy(() -> parser.parse(manifest("\"name\": \"М\", \"compatibleSystems\": [" + systems + "]")))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("compatibleSystems");
    }

    @Test
    void requiresDownload() {
        assertThatThrownBy(() -> parser.parse("""
                {"id": "m", "name": "М", "version": "1.0.0"}
                """))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("download");
    }

    @Test
    void rejectsDownloadOnForeignHost() {
        assertThatThrownBy(() -> parser.parse("""
                {"id": "m", "name": "М", "version": "1.0.0", "download": "https://evil.example/m.zip"}
                """))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("download");
    }

    @Test
    void rejectsIdUnsafeForFolderName() {
        assertThatThrownBy(() -> parser.parse("""
                {"id": "../etc", "name": "М", "version": "1.0.0",
                 "download": "https://github.com/a/b/releases/download/v1/m.zip"}
                """))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("id");
    }

    @Test
    void rejectsNonSemanticVersion() {
        assertThatThrownBy(() -> parser.parse("""
                {"id": "m", "name": "М", "version": "latest",
                 "download": "https://github.com/a/b/releases/download/v1/m.zip"}
                """))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("version");
    }

    @Test
    void rejectsNonObjectJson() {
        assertThatThrownBy(() -> parser.parse("[1, 2]"))
                .isInstanceOf(InvalidManifestException.class);
        assertThatThrownBy(() -> parser.parse("<html>"))
                .isInstanceOf(InvalidManifestException.class);
    }

    private static String manifest(String nameFields) {
        return "{\"id\": \"m\", \"version\": \"1.0.0\", "
                + "\"download\": \"https://github.com/a/b/releases/download/v1/m.zip\", "
                + nameFields + "}";
    }
}
