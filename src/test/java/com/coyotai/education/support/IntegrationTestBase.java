package com.coyotai.education.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base for API integration tests: a real Spring context on a real MySQL, migrated by Flyway
 * from scratch and filled with the demo data, which serves as a known fixture.
 *
 * <p>Demo accounts: admin, academic, mentor (CA Inter A), mentor2 (CA Inter B, ACCA Skills A),
 * faculty (Accounts/Taxation in CA Inter A, Accounts in CA Inter B), faculty2, and students
 * ADM26001-ADM26015 (ADM26001-005 in CA Inter A; ADM26013-015 in CMA USA A, which has no
 * mentor or timetable). The extra demo batches have their own staff - mentor3 (CA Inter C,
 * CMA India A), mentor4 (CS Executive A, ACCA Skills B), mentor5 (CA Inter D, CMA USA B),
 * mentor6 (CMA India B, CS Executive B), faculty3 to faculty5 - so they never change what the
 * accounts above can see. Students from ADM26016 are the extra batches' and five more in CA
 * Inter A, four more each in CA Inter B and ACCA Skills A. Every batch with a mentor has the
 * last 60 days of attendance; today is left for the tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(RequiresDatabase.class)
@Import(IntegrationTestBase.CleanDatabase.class)
public abstract class IntegrationTestBase {

    protected static final String STUDENT_PASSWORD = "student123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase db = TestDatabase.get();
        if (db != null) {
            registry.add("spring.datasource.url", db::url);
            registry.add("spring.datasource.username", db::username);
            registry.add("spring.datasource.password", db::password);
        }
    }

    /** Wipes and re-migrates the test database once, when the (shared) context starts. */
    @TestConfiguration
    static class CleanDatabase {

        @Bean
        FlywayMigrationStrategy cleanThenMigrate() {
            return (Flyway flyway) -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    protected String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "password", password)))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).path("data").path("accessToken").asText();
        if (token.isBlank()) {
            throw new IllegalStateException("Login failed for " + username + ": " + body);
        }
        return "Bearer " + token;
    }

    protected ResultActions getAs(String token, String url, Object... vars) throws Exception {
        return mockMvc.perform(withAuth(get(url, vars), token));
    }

    protected ResultActions postAs(String token, String url, String body, Object... vars) throws Exception {
        return mockMvc.perform(withAuth(post(url, vars), token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions putAs(String token, String url, String body, Object... vars) throws Exception {
        return mockMvc.perform(withAuth(put(url, vars), token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }

    protected String adminToken() throws Exception {
        return login("admin", "admin123");
    }

    /** The first element of a JSON array whose field equals the value. */
    protected JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode element : array) {
            if (value.equals(element.path(field).asText())) {
                return element;
            }
        }
        throw new IllegalStateException("No element with " + field + " = " + value + " in " + array);
    }

    protected long batchId(String name) throws Exception {
        return find(data(getAs(adminToken(), "/api/batches")), "name", name).path("id").asLong();
    }

    protected long subjectId(String name) throws Exception {
        return find(data(getAs(adminToken(), "/api/subjects")), "name", name).path("id").asLong();
    }

    protected long facultyId(String fullName) throws Exception {
        return find(data(getAs(adminToken(), "/api/faculty")), "fullName", fullName).path("id").asLong();
    }

    protected long studentId(String admissionNumber) throws Exception {
        JsonNode students = data(getAs(adminToken(), "/api/students?search={q}", admissionNumber));
        return find(students, "admissionNumber", admissionNumber).path("id").asLong();
    }

    /** Values of one field across a JSON array, e.g. every student's admission number. */
    protected List<String> values(JsonNode array, String... path) {
        List<String> values = new ArrayList<>();
        for (JsonNode element : array) {
            JsonNode node = element;
            for (String step : path) {
                node = node.path(step);
            }
            values.add(node.asText());
        }
        return values;
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, String token) {
        return token == null ? builder : builder.header("Authorization", token);
    }

    /** Builds a JSON object from key/value pairs; values may be JsonNode for nesting. */
    protected String json(Object... pairs) throws Exception {
        var node = objectMapper.createObjectNode();
        for (int i = 0; i < pairs.length; i += 2) {
            node.set(String.valueOf(pairs[i]), objectMapper.valueToTree(pairs[i + 1]));
        }
        return objectMapper.writeValueAsString(node);
    }
}
