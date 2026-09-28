package com.paynest.user.controller;

import com.paynest.user.dto.CreateUserRequest;
import com.paynest.user.dto.UserResponse;
import com.paynest.user.exception.DuplicateEmailException;
import com.paynest.user.exception.UserNotFoundException;
import com.paynest.user.model.User;
import com.paynest.user.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paynest.user.dto.UpdateProfileRequest;
import org.springframework.web.bind.annotation.PutMapping;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The web edge. Everything in this class is TRANSLATION:
// HTTP in -> method call down -> Java value back -> status code out.
// No business decisions live here.
@RestController
@RequestMapping("/users")
public class UserController {

    // Constructor injection, same as UserService holds its repository.
    // Write: a private final UserService field, and a constructor that takes one.
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User savedUser = service.register(request);

        URI location = URI.create("/users/" + savedUser.getEmail());
        return ResponseEntity.created(location).body(UserResponse.from(savedUser));
    }

    //    read all
    @GetMapping
    public List<UserResponse> findAll() {
        return service.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @GetMapping("/{email}")
    public UserResponse findByEmail(@PathVariable String email) {
        return UserResponse.from(service.getByEmail(email));
    }

    /**
     * Edits a user's profile. {@code PUT /users/{email}}.
     *
     * <p>PUT, not POST: this replaces the complete editable state of an existing
     * resource and is idempotent — sending the identical body twice leaves the
     * server in the same state as sending it once.
     *
     * <p>200, not 201 or 204: nothing was created, and returning the profile
     * saves the caller a follow-up GET and exposes {@code getUsername()}, which
     * they cannot derive themselves.
     *
     * <p>{@code email} comes from the path, not the body — {@code UpdateProfileRequest}
     * cannot carry one anyway, so there is no value that could disagree with it.
     */
    @PutMapping("/{email}")
    public UserResponse updateProfile(
            @PathVariable String email,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return UserResponse.from(service.updateProfile(email, request));
    }

    //    delete
    @DeleteMapping("/{email}")
    public ResponseEntity<Void> delete(@PathVariable String email) {
        service.deleteByEmail(email);
        return ResponseEntity.noContent().build();
    }

//    exceptions

    //    400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }
}
