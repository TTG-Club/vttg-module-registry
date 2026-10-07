package club.ttg.moduleregistry;

import club.ttg.moduleregistry.catalog.CatalogController;
import club.ttg.moduleregistry.catalog.CatalogService;
import club.ttg.moduleregistry.common.ApiExceptionHandler;
import club.ttg.moduleregistry.config.SecurityConfiguration;
import club.ttg.moduleregistry.manifest.InvalidManifestException;
import club.ttg.moduleregistry.submission.SubmissionController;
import club.ttg.moduleregistry.submission.SubmissionModerationController;
import club.ttg.moduleregistry.submission.SubmissionNotFoundException;
import club.ttg.moduleregistry.submission.SubmissionService;
import club.ttg.moduleregistry.system.GameSystemController;
import club.ttg.moduleregistry.system.GameSystemService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        CatalogController.class,
        GameSystemController.class,
        SubmissionController.class,
        SubmissionModerationController.class
})
@Import({SecurityConfiguration.class, ApiExceptionHandler.class})
@TestPropertySource(properties = {
        "auth-service.jwt-secret=" + ApiSecurityTest.SECRET,
        "cors.allowed-origins=https://new.ttg.club"
})
class ApiSecurityTest {

    static final String SECRET = "0123456789abcdef0123456789abcdef";

    private static final String VALID_SUBMISSION = """
            {
              "manifestUrl": "https://github.com/a/b/blob/main/module.json",
              "description": "Импорт карт",
              "systemIds": ["dnd5e-2024"]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private GameSystemService gameSystemService;

    @MockitoBean
    private SubmissionService submissionService;

    @Test
    void guestReadsCatalogFilteredBySystem() throws Exception {
        given(catalogService.find("dnd5e-2024")).willReturn(List.of());

        mockMvc.perform(get("/api/v1/modules").param("system", "dnd5e-2024"))
                .andExpect(status().isOk());

        verify(catalogService).find("dnd5e-2024");
    }

    @Test
    void catalogAllowsAnyOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/modules")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void submissionsAllowOnlySiteOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/submissions")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());

        mockMvc.perform(options("/api/v1/submissions")
                        .header(HttpHeaders.ORIGIN, "https://new.ttg.club")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk());
    }

    @Test
    void guestReadsSystems() throws Exception {
        mockMvc.perform(get("/api/v1/systems")).andExpect(status().isOk());
    }

    @Test
    void guestCannotSubmit() throws Exception {
        mockMvc.perform(post("/api/v1/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SUBMISSION))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(submissionService);
    }

    @Test
    void userSubmitsUnderOwnId() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/submissions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SUBMISSION))
                .andExpect(status().isCreated());

        verify(submissionService).submit(eq(userId), eq("tester"), any());
    }

    @Test
    void submissionValidatesFields() throws Exception {
        mockMvc.perform(post("/api/v1/submissions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"manifestUrl\": \"\", \"systemIds\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.manifestUrl").exists())
                .andExpect(jsonPath("$.errors.description").exists());
    }

    @Test
    void badManifestIsUnprocessable() throws Exception {
        given(submissionService.submit(any(), any(), any()))
                .willThrow(new InvalidManifestException("download: обязательное поле"));

        mockMvc.perform(post("/api/v1/submissions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SUBMISSION))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("download: обязательное поле"));
    }

    @Test
    void userSeesOnlyOwnSubmissionWhileModeratorSeesAny() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        given(submissionService.findVisible(submissionId, userId, false))
                .willThrow(new SubmissionNotFoundException(submissionId));

        mockMvc.perform(get("/api/v1/submissions/{id}", submissionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId, "USER")))
                .andExpect(status().isNotFound());

        UUID moderatorId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/submissions/{id}", submissionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderatorId, "MODERATOR")))
                .andExpect(status().isOk());
        verify(submissionService).findVisible(submissionId, moderatorId, true);
    }

    @Test
    void userCannotModerate() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/submissions/{id}/approve", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/moderation/submissions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(submissionService);
    }

    @Test
    void adminApprovesWithoutBodyAndRejectsWithComment() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/moderation/submissions/{id}/approve", submissionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminId, "ADMIN")))
                .andExpect(status().isOk());
        verify(submissionService).approve(eq(submissionId), eq(adminId), isNull());

        mockMvc.perform(post("/api/v1/moderation/submissions/{id}/reject", submissionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminId, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\": \"Нет README\"}"))
                .andExpect(status().isOk());
        verify(submissionService).reject(submissionId, adminId, "Нет README");
    }

    @Test
    void onlyAdminManagesSystems() throws Exception {
        String body = "{\"id\": \"pf2e\", \"name\": \"Pathfinder 2e\"}";

        mockMvc.perform(post("/api/v1/admin/systems")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "MODERATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/systems")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void systemIdMustMatchManifestFormat() throws Exception {
        mockMvc.perform(post("/api/v1/admin/systems")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"D&D 5e\", \"name\": \"D&D\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").exists());
    }

    private static String bearer(UUID userId, String... roles) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("username", "tester")
                .claim("roles", List.of(roles))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + token;
    }
}
