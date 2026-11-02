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
