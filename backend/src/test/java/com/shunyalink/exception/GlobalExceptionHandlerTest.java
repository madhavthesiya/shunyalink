package com.shunyalink.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("JDBC url=jdbc:postgresql://db.internal:5432 password=hunter2");
        }

        @GetMapping("/duplicate")
        String duplicate() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint \"users_username_key\"");
        }

        @GetMapping("/conflict/{constraint}")
        String conflict(@PathVariable String constraint) {
            throw new DataIntegrityViolationException("could not execute statement",
                    new org.hibernate.exception.ConstraintViolationException("could not execute statement",
                            new java.sql.SQLException("duplicate key"), constraint));
        }

        /** What PostgreSQL really says for a duplicate link: the detail repeats the whole URL, token included. */
        @GetMapping("/duplicate-link")
        String duplicateLink() {
            java.sql.SQLException postgres = new java.sql.SQLException("ERROR: duplicate key value violates unique "
                    + "constraint \"idx_long_url_non_custom\"\n  Detail: Key (long_url)=(https://bank.example/reset?token=s3cr3t-reset-token) already exists.");
            throw new DataIntegrityViolationException("could not execute statement",
                    new org.hibernate.exception.ConstraintViolationException("could not execute statement", postgres,
                            "idx_long_url_non_custom"));
        }

        @PostMapping("/json")
        String json(@RequestBody java.util.Map<String, String> body) {
            return "ok";
        }

        @GetMapping("/param")
        String param(@RequestParam int page) {
            return "ok";
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void unexpectedError_returns500WithoutLeakingInternals() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.GENERIC_ERROR))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void constraintViolation_logsTheConstraintName_neverTheValuesInIt(org.springframework.boot.test.system.CapturedOutput logs)
            throws Exception {
        // URLs can carry reset tokens or session IDs; the logs must never contain them (ADR 0019).
        mockMvc.perform(get("/duplicate-link")).andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(logs.getAll())
                .contains("idx_long_url_non_custom")
                .doesNotContain("s3cr3t-reset-token")
                .doesNotContain("bank.example");
    }

    @Test
    void errors_areRfc9457ProblemDetails_andKeepTheFieldsTheFrontendReads() throws Exception {
        // Standard format (type, title, status, detail) plus the old fields, so existing clients keep working.
        mockMvc.perform(get("/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void aConflict_saysWhatConflicts_whenTheConstraintIsKnown() throws Exception {
        mockMvc.perform(get("/conflict/users_username_key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That username is already taken."));
        mockMvc.perform(get("/conflict/users_email_key"))
                .andExpect(jsonPath("$.message").value("An account with this email already exists."));
        mockMvc.perform(get("/conflict/urls_short_id_key"))
                .andExpect(jsonPath("$.message").value("That alias is already taken."));
        mockMvc.perform(get("/conflict/some_other_constraint"))
                .andExpect(jsonPath("$.message").value("This conflicts with existing data. Please try again."));
    }

    @Test
    void constraintViolation_returns409WithoutTheSqlMessage() throws Exception {
        mockMvc.perform(get("/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("users_username_key"))));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/json").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingOrInvalidParameter_returns400() throws Exception {
        mockMvc.perform(get("/param")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/param").param("page", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void wrongHttpMethod_returns405() throws Exception {
        mockMvc.perform(delete("/boom")).andExpect(status().isMethodNotAllowed());
    }
}
