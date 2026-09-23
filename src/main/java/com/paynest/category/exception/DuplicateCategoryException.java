package com.paynest.category.exception;

// =========================================================================
//  DAY-05, STEP 2.5g — DuplicateCategoryException
// =========================================================================
//  Model this on your existing UserNotFoundException /
//  DuplicateEmailException. Same three properties:
//
//    1. extends RuntimeException (unchecked). Nothing between the service
//       and the controller can meaningfully recover, so a checked
//       exception would only buy "throws" clauses on a dozen signatures.
//
//    2. CARRIES THE NAME AS DATA, not only inside a message string:
//           private final String name;      + a getName() accessor
//       so the handler can build a response without parsing English back
//       out of it. Slice 4's rule, and it paid out on Day-05 when the 409
//       body could carry the email as a structured field.
//
//    3. A DEDICATED TYPE, because you cannot catch a string, and
//       catch(RuntimeException) would also swallow NPEs and dead
//       connections. The type IS the routing key for @ExceptionHandler.
// =========================================================================
package com.paynest.category.exception;

public class DuplicateCategoryException extends RuntimeException {

    private final String name;

    public DuplicateCategoryException(String name) {
        super("A category already exists with name: " + name);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}