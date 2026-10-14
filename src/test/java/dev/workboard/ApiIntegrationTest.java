package dev.workboard;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import dev.workboard.infrastructure.persistence.JpaStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired JwtEncoder encoder;
  @MockitoSpyBean JpaStore store;
  private static final String PASSWORD = "CorrectHorseBattery42!";
  private String token, project, task;

  @BeforeEach
  void setup() throws Exception {
    reset(store);
    for (String table : List.of("task_events", "tasks", "projects", "app_users"))
      jdbc.update("DELETE FROM " + table);
    token = account("owner@example.com");
    project = field(send(post("/api/v1/projects"), "{\"name\":\"Launch\"}", 201), "id");
    task =
        field(
            send(post(tasks()), "{\"title\":\"Ship the API\",\"description\":\"Reviewed\"}", 201),
            "id");
  }

  private String account(String email) throws Exception {
    String body = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
    mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(body))
        .andExpect(status().isCreated());
    return field(
        mvc.perform(post("/api/v1/auth/token").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andReturn(),
        "accessToken");
  }

  private MvcResult send(MockHttpServletRequestBuilder request, String body, int status)
      throws Exception {
    request.header("Authorization", "Bearer " + token);
    if (body != null) request.contentType("application/json").content(body);
    return mvc.perform(request).andExpect(status().is(status)).andReturn();
  }

  private String field(MvcResult result, String name) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$." + name);
  }

  private String tasks() {
    return "/api/v1/projects/" + project + "/tasks";
  }

  private String taskPath() {
    return tasks() + "/" + task;
  }

  @Test
  void passwordsAreHashedAndNeverReturned() throws Exception {
    String response = send(get("/api/v1/auth/me"), null, 200).getResponse().getContentAsString();
    assertFalse(response.contains("password"));
    assertTrue(
        jdbc.queryForObject(
                "SELECT password_hash FROM app_users WHERE email='owner@example.com'", String.class)
            .startsWith("$2"));
    assertFalse(response.contains(PASSWORD));
  }

  @Test
  void emailNormalizationAndDuplicateProtection() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType("application/json")
                .content("{\"email\":\"OWNER@EXAMPLE.COM\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void incorrectAndUnknownLoginHaveSameFailure() throws Exception {
    for (String email : List.of("owner@example.com", "absent@example.com")) {
      mvc.perform(
              post("/api/v1/auth/token")
                  .contentType("application/json")
                  .content("{\"email\":\"" + email + "\",\"password\":\"WrongPassword42!\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }
  }

  @Test
  void missingAndMalformedBearerTokensAreRejected() throws Exception {
    mvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer junk"))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"expired", "audience", "issuer", "subject", "missing"})
  void invalidJwtClaimsAreRejected(String mutation) throws Exception {
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .subject("subject".equals(mutation) ? "bad" : UUID.randomUUID().toString())
            .issuer("issuer".equals(mutation) ? "another-issuer" : "workboard-api")
            .audience(List.of("audience".equals(mutation) ? "another-service" : "workboard-client"))
            .issuedAt(now.minusSeconds(300));
    if (!"missing".equals(mutation))
      claims.expiresAt("expired".equals(mutation) ? now.minusSeconds(120) : now.plusSeconds(900));
    String value =
        encoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
            .getTokenValue();
    mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + value))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void ownerIsolationCoversEveryNestedRoute() throws Exception {
    token = account("outsider@example.com");
    send(get("/api/v1/projects/" + project), null, 404);
    send(delete("/api/v1/projects/" + project), null, 404);
    send(get(tasks()), null, 404);
    send(post(tasks()), "{\"title\":\"Intrusion\"}", 404);
    send(get(taskPath()), null, 404);
    send(patch(taskPath()), "{\"title\":\"Intrusion\",\"expectedVersion\":0}", 404);
    send(get(taskPath() + "/events"), null, 404);
    send(get("/api/v1/projects"), null, 200);
  }

  @Test
  void taskCannotBeAccessedThroughAnotherProject() throws Exception {
    String other = field(send(post("/api/v1/projects"), "{\"name\":\"Other\"}", 201), "id");
    send(get("/api/v1/projects/" + other + "/tasks/" + task), null, 404);
    send(get(tasks() + "/" + UUID.randomUUID()), null, 404);
    send(get("/api/v1/projects/" + UUID.randomUUID()), null, 404);
  }

  @Test
  void transitionsAndAuditAreVersionOrdered() throws Exception {
    send(patch(taskPath()), "{\"status\":\"IN_PROGRESS\",\"expectedVersion\":0}", 200);
    send(patch(taskPath()), "{\"status\":\"DONE\",\"expectedVersion\":1}", 200);
    send(patch(taskPath()), "{\"status\":\"IN_PROGRESS\",\"expectedVersion\":2}", 200);
    MvcResult result = send(get(taskPath() + "/events"), null, 200);
    assertEquals(
        List.of("TODO", "IN_PROGRESS", "DONE", "IN_PROGRESS"),
        JsonPath.read(result.getResponse().getContentAsString(), "$.items[*].toStatus"));
    assertEquals(4, (Integer) JsonPath.read(result.getResponse().getContentAsString(), "$.total"));
    send(get(taskPath() + "/events?size=1&page=1"), null, 200);
  }

  @Test
  void invalidTransitionAndStaleWriteChangeNothing() throws Exception {
    send(patch(taskPath()), "{\"status\":\"DONE\",\"expectedVersion\":0}", 422);
    send(patch(taskPath()), "{\"title\":\"New\",\"expectedVersion\":0}", 200);
    send(patch(taskPath()), "{\"title\":\"Stale\",\"expectedVersion\":0}", 409);
    MvcResult result = send(get(taskPath()), null, 200);
    assertEquals("New", field(result, "title"));
    assertEquals(
        1, (Integer) JsonPath.read(result.getResponse().getContentAsString(), "$.version"));
  }

  @Test
  void descriptionsCanBeClearedWithoutAddingStatusEvents() throws Exception {
    send(
        patch(taskPath()),
        "{\"clearDescription\":true,\"status\":\"TODO\",\"expectedVersion\":0}",
        200);
    assertNull(
        JsonPath.read(
            send(get(taskPath()), null, 200).getResponse().getContentAsString(), "$.description"));
    assertEquals(
        1,
        (Integer)
            JsonPath.read(
                send(get(taskPath() + "/events"), null, 200).getResponse().getContentAsString(),
                "$.total"));
  }

  @Test
  void filtersAndPaginationStayScoped() throws Exception {
    send(patch(taskPath()), "{\"status\":\"IN_PROGRESS\",\"expectedVersion\":0}", 200);
    send(post(tasks()), "{\"title\":\"Another\"}", 201);
    assertEquals(
        1,
        (Integer)
            JsonPath.read(
                send(get(tasks() + "?status=TODO"), null, 200).getResponse().getContentAsString(),
                "$.total"));
    send(get(tasks() + "?status=DONE"), null, 200);
    send(get(tasks() + "?size=1&page=1"), null, 200);
    send(get("/api/v1/projects?size=1&page=0"), null, 200);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"expectedVersion\":0}",
        "{\"title\":\" \" ,\"expectedVersion\":0}",
        "{\"title\":\"X\",\"expectedVersion\":-1}",
        "{\"title\":\"X\"}",
        "{\"description\":\"X\",\"clearDescription\":true,\"expectedVersion\":0}"
      })
  void invalidPatchesAreRejected(String body) throws Exception {
    send(patch(taskPath()), body, 422);
  }

  @ParameterizedTest
  @ValueSource(strings = {"size=0", "size=101", "page=-1", "page=501"})
  void paginationIsBounded(String query) throws Exception {
    send(get(tasks() + "?" + query), null, 422);
  }

  @Test
  void malformedRequestsAndWhitespaceAreHandled() throws Exception {
    send(post(tasks()), "{\"title\":\" \"}", 422);
    send(post("/api/v1/projects"), "{\"name\":\" \"}", 422);
    send(get(tasks() + "?status=INVALID"), null, 400);
    send(patch(taskPath()), "{broken", 400);
    send(post(tasks()), "{\"title\":\" X \"}", 201);
    send(post("/api/v1/projects"), "{\"name\":\"X\",\"ownerId\":\"ignored\"}", 400);
  }

  @Test
  void validationDoesNotEchoCredentials() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType("application/json")
                    .content("{\"email\":\"bad\",\"password\":\"short-secret\"}"))
            .andExpect(status().is(422))
            .andReturn();
    assertFalse(result.getResponse().getContentAsString().contains("short-secret"));
  }

  @Test
  void oversizedUtf8PasswordsAreRejected() throws Exception {
    String body = "{\"email\":\"long@example.com\",\"password\":\"" + "é".repeat(40) + "\"}";
    mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(body))
        .andExpect(status().is(422));
  }

  @Test
  void deleteCascadesTasksAndAudit() throws Exception {
    send(delete("/api/v1/projects/" + project), null, 204);
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM task_events", Integer.class));
    send(delete("/api/v1/projects/" + project), null, 404);
  }

  @Test
  void auditFailureRollsBackTheTask() throws Exception {
    doThrow(new IllegalStateException("sensitive exception details"))
        .when(store)
        .recordEvent(any());
    MvcResult result =
        send(patch(taskPath()), "{\"status\":\"IN_PROGRESS\",\"expectedVersion\":0}", 500);
    assertFalse(result.getResponse().getContentAsString().contains("sensitive exception"));
    assertEquals("TODO", field(send(get(taskPath()), null, 200), "status"));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT version FROM tasks WHERE id=?", Integer.class, UUID.fromString(task)));
  }

  @Test
  void losingAtomicRaceReturnsConflict() throws Exception {
    doReturn(false).when(store).compareAndSetTask(any(), anyLong());
    send(patch(taskPath()), "{\"title\":\"Lost race\",\"expectedVersion\":0}", 409);
  }

  @Test
  void healthDocsAndCorrelationIdsWork() throws Exception {
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("Workboard API"));
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    String id = UUID.randomUUID().toString();
    mvc.perform(
            get("/api/v1/projects/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token)
                .header("X-Request-ID", id))
        .andExpect(status().isNotFound())
        .andExpect(header().string("X-Request-ID", id))
        .andExpect(jsonPath("$.requestId").value(id));
    MvcResult invalid =
        mvc.perform(get("/actuator/health/liveness").header("X-Request-ID", "unsafe"))
            .andExpect(status().isOk())
            .andReturn();
    assertDoesNotThrow(() -> UUID.fromString(invalid.getResponse().getHeader("X-Request-ID")));
  }
}
