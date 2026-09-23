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

    // =====================================================================
    //  DAY-05, STEP 3b — add @Valid to the parameter below.
    // =====================================================================
    //  Import jakarta.validation.Valid, then write:
    //
    //      create(@Valid @RequestBody CreateUserRequest request)
    //
    //  WITHOUT THIS ANNOTATION, STEP 3a DID NOTHING. The constraints you
    //  just wrote are inert metadata; @Valid is what tells Spring to run
    //  them. Miss it and you get: no error, no log line, a green build, and
    //  zero validation.
    //
    //  That is the FOURTH time this exact shape has appeared:
    //      Day-03  flyway-core present, spring-boot-flyway missing
    //      Day-03  Lombok on the classpath, processor not running
    //      Day-04  properties written, file never loaded
    //      Day-05  annotations written, @Valid missing
    //
    //  "Present but not participating." You now have a name for it, and
    //  this is the first time you can see it coming rather than debug it
    //  afterwards.
    //
    //  ⚠️ SO STEP 3 IS NOT DONE WHEN IT COMPILES. It is done when you have
    //  made it FAIL ON PURPOSE. Instructions at the bottom of this file.
    // =====================================================================
    //    create
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User savedUser = service.register(new User(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.password()));

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

    //    delete
    @DeleteMapping("/{email}")
    public ResponseEntity<Void> delete(@PathVariable String email) {
        service.deleteByEmail(email);
        return ResponseEntity.noContent().build();
    }

//    exceptions

    //    409
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateEmailException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    //    404
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    //    400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }

    // =====================================================================
    //  DAY-05, STEP 3c — a handler for the exception @Valid throws.
    // =====================================================================
    //  When validation fails, Spring throws
    //  org.springframework.web.bind.MethodArgumentNotValidException.
    //  You have no handler for it, so right now it would become a 500 —
    //  the server confessing to the caller's mistake.
    //
    //  4xx = you did something wrong, and you can fix it.
    //  5xx = I did something wrong, and only I can fix it.
    //
    //  An incomplete handler list does not just produce a wrong number; it
    //  MISATTRIBUTES BLAME, and buries real defects among false ones. On
    //  Day-19 somebody gets paged on a 500 rate.
    //
    //  ---------------------------------------------------------------
    //  WHAT TO WRITE. One method, same shape as the three above:
    //
    //      @ExceptionHandler(MethodArgumentNotValidException.class)
    //      public ResponseEntity<Map<String, String>> handleValidation(
    //              MethodArgumentNotValidException e) { ... }
    //
    //  Inside it, do NOT flatten the result to one message. @Valid collects
    //  EVERY violation — six bad fields arrive as one exception carrying
    //  six failures:
    //
    //      MethodArgumentNotValidException
    //        +- BindingResult
    //             +- FieldError  field:"firstName"  message:"..."
    //             +- FieldError  field:"email"      message:"..."
    //
    //  Returning a single string would mean collecting six answers and
    //  throwing away five. Build a Map<String,String> of field -> message
    //  instead, so a form can highlight three inputs at once:
    //
    //      e.getBindingResult().getFieldErrors()   gives you the list;
    //      each one has .getField() and .getDefaultMessage().
    //
    //  AN ERROR IS DATA. The moment you concatenate it into prose, every
    //  consumer has to parse English back out of it — the same reason your
    //  DuplicateEmailException carries getEmail() as a field rather than
    //  only a message.
    //
    //  ⚠️ USE A LinkedHashMap AND putIfAbsent, NOT Map.of(). Two reasons,
    //  and the second is not hypothetical: Map.of() THROWS on a duplicate
    //  key, and `password` has TWO constraints (@NotBlank and @Size), so a
    //  single blank password produces two FieldErrors for one field. Your
    //  error handler would then throw while reporting an error.
    //  (LinkedHashMap also preserves order, so fields come back in
    //  declaration order rather than a hash order that shifts between runs.)
    //
    //  Return ResponseEntity.badRequest().body(errors).
    // =====================================================================


    // =====================================================================
    //  STEP 3 IS NOT DONE UNTIL YOU HAVE PROVEN IT RUNS.
    // =====================================================================
    //  ./mvnw clean test      -> expect 38 green (nothing tests this yet)
    //
    //  Then start the app and POST something invalid:
    //
    //      ./mvnw spring-boot:run
    //
    //      curl -i -X POST http://localhost:8090/users \
    //        -H 'Content-Type: application/json' \
    //        -d '{"firstName":"","lastName":"","email":"nope","password":"x"}'
    //
    //  EXPECT 400, and a body naming ALL FOUR fields:
    //
    //      {"firstName":"...","lastName":"...",
    //       "email":"...","password":"..."}
    //
    //  IF YOU GET 201 -> @Valid is missing or the starter is not resolving.
    //  IF YOU GET 400 WITH ONE FIELD -> that is the MODEL's requireText
    //     throwing, not bean validation. @Valid is not wired.
    //  IF YOU GET 500 -> the handler above is missing.
    //
    //  Read the difference between those three carefully. All three are
    //  "it didn't work", and each one points at a different missing piece.
    // =====================================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();

        e.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(errors);
    }
}
