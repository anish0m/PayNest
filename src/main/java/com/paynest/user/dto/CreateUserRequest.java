package com.paynest.user.dto;

// =========================================================================
//  DAY-05, STEP 3a — add the constraint annotations.
// =========================================================================
//  You need three imports from jakarta.validation.constraints:
//      Email, NotBlank, Size
//
//  Write them above (the `jakarta.` prefix, not `javax.` — javax is the
//  pre-2020 name and every older tutorial still uses it; it will not
//  resolve here).
// =========================================================================

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// A record: a name for a group of values, no behaviour.
// Java generates the constructor, accessors, equals, hashCode, toString.
// Fields are final. Jackson understands records natively.
//
// Field order follows YOUR User constructor: firstName, lastName, email, password.
//
// =========================================================================
//  WHY THIS IS NOT DUPLICATING User's requireText()
// =========================================================================
//  Your first instinct will be that these annotations say the same thing
//  the model already says. They do not, and DELETING EITHER ONE causes a
//  real defect. Two different questions:
//
//    @NotBlank here            "Did the CALLER send something usable?"
//                              -> 400. Runs once, at the HTTP edge.
//                              -> Removed by deleting a line.
//
//    requireText() in User     "Can this OBJECT exist in this state AT ALL?"
//                              -> IllegalArgumentException. A BUG, not a
//                                 bad request. Runs on every construction
//                                 and every setter, forever, and no caller
//                                 can bypass it.
//
//  DELETE THESE and the caller still gets a 400, because the model throws
//  and your handler maps it. What you lose is the MESSAGE. requireText is a
//  `throw`, and a throw ends the method — so it reports the FIRST failure
//  only. The caller fixes one field, resubmits, discovers the next. Bean
//  validation collects EVERY violation before failing.
//
//      Fail-fast is right for a bug. Fail-completely is right for a form.
//
//  DELETE THE MODEL'S GUARD and the only check lives on the HTTP boundary —
//  and Day-13's scheduler does not go through a controller. There is no
//  @Valid anywhere on that path.
//
//  This is POLICY vs GUARANTEE for the third time:
//      Day-01  service duplicate check   | nothing behind it
//      Day-03  service duplicate check   | UNIQUE constraint
//      Day-05  these annotations         | requireText in User
// =========================================================================
public record CreateUserRequest(

        // -----------------------------------------------------------------
        //  firstName — @NotBlank + @Size(max = 100)
        //
        //  WHY @NotBlank AND NOT THE OTHER TWO. They are not
        //  interchangeable, and the middle one is a trap:
        //
        //    @NotNull   rejects null only           -> ""    passes
        //    @NotEmpty  rejects null and ""         -> "   " passes
        //    @NotBlank  rejects null, "" and "   "  -> what a name needs
        //
        //  WHY @Size(max = 100). It mirrors VARCHAR(100) in your V1.
        //  Without it a 200-character name passes validation, reaches the
        //  INSERT, and fails as a driver-level error — a 500 for what is
        //  unambiguously the caller's mistake. The column is still the
        //  guarantee; this just makes the failure arrive at the right layer
        //  wearing the right status code.
        //
        //  Give each one a `message = "..."`. The default ("must not be
        //  blank") does not name the field.
        // -----------------------------------------------------------------
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        // -----------------------------------------------------------------
        //  lastName — same two annotations, same reasoning.
        // -----------------------------------------------------------------
        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName,

        // -----------------------------------------------------------------
        //  email — @NotBlank + @Email
        //
        //  BOTH, and the reason is a genuine quirk: @Email on its own
        //  ACCEPTS THE EMPTY STRING. The spec takes the view that "absent"
        //  is @NotNull's job, so @Email only judges strings that are there.
        //  Two annotations, two separate claims.
        //
        //  ⚠️ @Email IS A SHAPE CHECK, NOT AN EXISTENCE CHECK. "a@b" passes.
        //  It tells you an address LOOKS like an address; only sending mail
        //  tells you it IS one. Worth being precise, because the natural
        //  reading of a green validation is stronger than the truth.
        // -----------------------------------------------------------------
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        // -----------------------------------------------------------------
        //  password — @NotBlank + @Size(min = 8, max = 72)
        //
        //  The only field whose bound is a POLICY rather than a description
        //  of the column. The stored value is a 60-char hash no matter how
        //  long the input was, so max = 72 is not about storage.
        //
        //  It is about BCrypt: the algorithm SILENTLY TRUNCATES input past
        //  72 bytes. Without this bound, two different long passwords that
        //  share their first 72 bytes both authenticate — a silent
        //  weakening, no error anywhere. Your least favourite kind of bug,
        //  and now you can forbid it in one annotation.
        //
        //  min = 8 is deliberately WEAK, and saying so matters more than the
        //  number. Length is poor evidence of strength ("password" is eight
        //  characters). Real policy — breach lists, rate limiting, MFA — is
        //  Day-06. This is a floor, not a claim.
        //
        //  NOTE THE FIELD IS STILL CALLED `password`, NOT passwordHash.
        //  That is correct and deliberate: this is what the CALLER sends,
        //  and the caller sends a plaintext password. The rename was about
        //  STORAGE. A DTO describes the wire, not the table — this is the
        //  first place in PayNest where the two genuinely differ.
        // -----------------------------------------------------------------
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password) {
}
