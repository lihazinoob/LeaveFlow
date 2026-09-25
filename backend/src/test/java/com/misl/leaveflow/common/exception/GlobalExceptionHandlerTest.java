package com.misl.leaveflow.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that each exception type is turned into the expected HTTP status and ApiError body.
 * Security filters are disabled here because only the error mapping is under test.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ThrowingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resourceNotFound_returns404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Employee not found with id 42"))
                .andExpect(jsonPath("$.path").value("/test/not-found"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void businessRuleViolation_returns400() throws Exception {
        mockMvc.perform(get("/test/business-rule"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient leave balance"));
    }

    @Test
    void conflict_returns409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void accessDenied_returns403() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void invalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
    }

    @Test
    void wrongParameterType_returns400() throws Exception {
        mockMvc.perform(get("/test/typed").param("year", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'abc' for parameter 'year'"));
    }

    @Test
    void unexpectedError_returns500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    record SampleRequest(@NotBlank(message = "Name is required") String name) {
    }

    @RestController
    @RequestMapping("/test")
    static class ThrowingController {

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Employee", 42);
        }

        @GetMapping("/business-rule")
        void businessRule() {
            throw new BusinessRuleException("Insufficient leave balance");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("Email already in use");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new AccessDeniedException("denied");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody SampleRequest request) {
        }

        @GetMapping("/typed")
        void typed(@RequestParam int year) {
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("database password is hunter2");
        }
    }
}
