package club.ttg.moduleregistry.manifest;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlPolicyTest {

    private final UrlPolicy policy = new UrlPolicy(properties());

    static ManifestProperties properties() {
        return new ManifestProperties(
                List.of("github.com", "raw.githubusercontent.com", " GitLab.com "), null, null, null);
    }

    @Test
    void acceptsHttpsOnAllowedHost() {
        assertThat(policy.require("https://gitlab.com/a/b", "repositoryUrl"))
                .isEqualTo(URI.create("https://gitlab.com/a/b"));
    }

    @Test
    void rejectsPlainHttp() {
        assertThatThrownBy(() -> policy.require("http://github.com/a/b", "repositoryUrl"))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("https");
    }

    @Test
    void rejectsForeignAndLookalikeHosts() {
        assertThatThrownBy(() -> policy.require("https://localhost/module.json", "manifestUrl"))
                .isInstanceOf(InvalidManifestException.class);
        assertThatThrownBy(() -> policy.require("https://github.com.evil.example/m.json", "manifestUrl"))
                .isInstanceOf(InvalidManifestException.class);
        assertThatThrownBy(() -> policy.require("https://169.254.169.254/latest", "manifestUrl"))
                .isInstanceOf(InvalidManifestException.class);
    }

    @Test
    void rejectsCredentialsAndCustomPorts() {
        assertThatThrownBy(() -> policy.require("https://user:pass@github.com/a", "manifestUrl"))
                .isInstanceOf(InvalidManifestException.class);
        assertThatThrownBy(() -> policy.require("https://github.com:8443/a", "manifestUrl"))
                .isInstanceOf(InvalidManifestException.class);
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> policy.require("not a url", "manifestUrl"))
                .isInstanceOf(InvalidManifestException.class);
    }

    @Test
    void turnsGithubBlobPageIntoRawFile() {
        URI blob = URI.create("https://github.com/ttg/map-import/blob/main/module.json");

        assertThat(policy.toRawFile(blob))
                .isEqualTo(URI.create("https://raw.githubusercontent.com/ttg/map-import/main/module.json"));
    }

    @Test
    void leavesOtherLinksAsIs() {
        URI release = URI.create("https://github.com/ttg/map-import/releases/latest/download/module.json");

        assertThat(policy.toRawFile(release)).isEqualTo(release);
    }
}
