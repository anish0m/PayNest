package com.paynest.category.exception;

// =========================================================================
//  DAY-05, STEP 2.5g — CategoryInUseException
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
//
//  ⚠️ THIS ONE IS DIFFERENT FROM THE OTHER TWO — it maps to 409, and it
//  is the only exception in PayNest so far that is thrown because THE
//  DATABASE REFUSED, not because the application decided.
//
//  The service catches DataIntegrityViolationException (the FK violation
//  from V4) and rethrows this. So the guarantee is the constraint; this
//  class only translates it into something the web layer can route on.
//
//  Its message should say what to do about it — "3 transfers still use
//  this category" — because unlike "not found", this one is actionable.
//  That means it wants a count as well as a name.
package com.paynest.category.exception;

public class CategoryInUseException extends RuntimeException {

    private final String name;
    private final long transferCount;

    public CategoryInUseException(String name, long transferCount) {
        super(transferCount + " transfers still use category: " + name);
        this.name = name;
        this.transferCount = transferCount;
    }

    public String getName() {
        return name;
    }

    public long getTransferCount() {
        return transferCount;
    }
}