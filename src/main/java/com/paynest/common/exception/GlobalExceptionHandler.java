package com.paynest.common.exception;

// =========================================================================
//  DAY-05, STEP 7 — GlobalExceptionHandler.
// =========================================================================
//  This is the move Day-02's comment predicted and deliberately deferred:
//  "Option 2 today [handlers per-controller], because there is exactly one
//   controller and moving to option 3 is then a genuine, visible
//   improvement rather than architecture applied in advance."
//
//  You now have TWO controllers with near-identical handler sets — a
//  *NotFoundException -> 404, a Duplicate*Exception -> 409, and an
//  IDENTICAL MethodArgumentNotValidException -> 400 handler copy-pasted
//  in both files. That duplication is no longer speculative; it exists.
//  This class removes it.
//
//  Package: com.paynest.common.exception — not user, not category. This
//  class belongs to NEITHER domain and BOTH controllers; putting it under
//  either package would make the dependency direction backwards (category
//  should not import from user, or vice versa, just to share an error
//  shape).
// =========================================================================


// -------------------------------------------------------------------------
//  IMPORTS YOU WILL NEED:
//
//      org.springframework.http.HttpStatus
//      org.springframework.http.ProblemDetail
//      org.springframework.web.bind.MethodArgumentNotValidException
//      org.springframework.web.bind.annotation.ExceptionHandler
//      org.springframework.web.bind.annotation.RestControllerAdvice
//
//      com.paynest.user.exception.DuplicateEmailException
//      com.paynest.user.exception.UserNotFoundException
//      com.paynest.category.exception.DuplicateCategoryException
//      com.paynest.category.exception.CategoryNotFoundException
//      com.paynest.category.exception.CategoryInUseException
//
//  ⚠️ NOTE THIS CLASS NOW IMPORTS FROM BOTH user.exception AND
//  category.exception. That is the honest cost of consolidation: one class
//  that knows about every exception type in the app, in exchange for zero
//  duplicated handler bodies. If that asymmetry ever feels wrong, the
//  alternative is a shared marker interface (e.g. NotFoundException) that
//  each domain's exceptions implement — a bigger change, and not what
//  today's step asks for. Worth knowing the escape hatch exists.
// -------------------------------------------------------------------------
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.paynest.category.exception.CategoryInUseException;
import com.paynest.category.exception.CategoryNotFoundException;
import com.paynest.category.exception.DuplicateCategoryException;
import com.paynest.user.exception.DuplicateEmailException;
import com.paynest.user.exception.UserNotFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleEmailDuplicate(DuplicateEmailException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        problem.setProperty("email", e.getEmail());
        return problem;
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ProblemDetail handleCategoryNotFound(CategoryNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(DuplicateCategoryException.class)
    public ProblemDetail handleCategoryDuplicate(DuplicateCategoryException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        problem.setProperty("category", e.getName());
        return problem;
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ProblemDetail handleCategoryInUse(CategoryInUseException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        problem.setProperty("name", e.getName());
        problem.setProperty("transferCount", e.getTransferCount());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();

        e.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Bad Request");
        problem.setProperty("errors", errors);
        return problem;
    }
}