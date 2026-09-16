package com.paynest.user.controller;

import com.paynest.user.exception.DuplicateEmailException;
import com.paynest.user.exception.UserNotFoundException;
import com.paynest.user.model.User;
import com.paynest.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest boots ONLY the web layer — this controller, Jackson, the
// exception handlers. Not the service, not the repository.
//
// @MockitoBean supplies a fake UserService, so a test can say "the service
// throws" without first registering a real user. What is under test is the
// TRANSLATION, not the business logic.
//
// MockMvc sends requests through real Spring MVC dispatch — routing, binding,
// JSON, exception handling — without opening a TCP socket.
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService service;

    private User anishom() {
        return new User("Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
    }

    private static final String VALID_JSON = """
            {
              "firstName": "Anishom",
              "lastName": "Frost",
              "email": "khi0ne@example.com",
              "password": "Pass1234#"
            }
            """;

    @Test
    void signupReturns201WithLocation() throws Exception {
        when(service.register(any(User.class))).thenReturn(anishom());

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/users/khi0ne@example.com"))
                .andExpect(jsonPath("$.email").value("khi0ne@example.com"))
                .andExpect(jsonPath("$.username").value("@anishom-frost"));
    }

    // Asserts what the response CANNOT contain. A password leak is invisible to
    // a normal test: every other assertion passes perfectly while the field sits
    // there in the JSON. Absence has to be asserted on purpose.
    @Test
    void responseNeverContainsThePassword() throws Exception {
        when(service.register(any(User.class))).thenReturn(anishom());

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // The controller contains NO duplicate check — the mock throws, exactly as the
    // real service does. This verifies the @ExceptionHandler routing table.
    @Test
    void duplicateEmailReturns409() throws Exception {
        when(service.register(any(User.class)))
                .thenThrow(new DuplicateEmailException("khi0ne@example.com"));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    // The collection exists and is empty. 200 [], never 404.
    @Test
    void listingWithNoUsersReturns200AndEmptyArray() throws Exception {
        when(service.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // The opposite of 409: does not exist, vs already exists.
    @Test
    void fetchingAMissingUserReturns404() throws Exception {
        when(service.getByEmail(anyString()))
                .thenThrow(new UserNotFoundException("nobody@example.com"));

        mockMvc.perform(get("/users/nobody@example.com"))
                .andExpect(status().isNotFound());
    }

    // 204 is a literal promise of no body — so the body is asserted empty.
    @Test
    void deletingReturns204WithNoBody() throws Exception {
        mockMvc.perform(delete("/users/khi0ne@example.com"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }
}
