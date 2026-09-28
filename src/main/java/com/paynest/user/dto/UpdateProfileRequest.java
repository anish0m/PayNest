package com.paynest.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What a profile-edit request is allowed to contain.
 *
 * <p>Deliberately has no {@code email} and no {@code password} field.
 *
 * <p><b>Why no email.</b> {@link com.paynest.user.model.User#getEmail()} is
 * final — it is the identity key everything else hangs off (equals/hashCode,
 * the UNIQUE constraint, the URL {@code /users/{email}}). If this DTO carried
 * an email field, a {@code PUT /users/old@x.com} with a different email in
 * the body would raise a question with no good answer: rename the user, or
 * reject as a 400? A DTO with no email field makes the question unaskable —
 * there is no value to disagree with the path, so there is nothing to
 * reconcile. Same move as {@code CreateUserRequest} having no {@code id}
 * field: the wrong thing is not validated against, it cannot be expressed.
 *
 * <p><b>Why no password.</b> Changing a password is a different operation
 * from editing a profile, even though both are technically "update the users
 * row" — it deserves its own route (Day-06, alongside login) with its own
 * rules (old password required, re-hash, possibly invalidating sessions).
 * Leaving it out of this DTO means a profile update literally cannot touch
 * the hash.
 *
 * <p><b>{@code image} is not {@code @NotBlank}</b>, unlike {@code firstName}
 * and {@code lastName}. PUT replaces all of a resource's editable state: if a
 * caller omits {@code image} from the JSON body, Jackson binds it to
 * {@code null}, and the service sets the user's image to {@code null}. That
 * is correct for PUT — omitting {@code image} is how a caller expresses
 * "clear it" — because {@code User.setImage} already treats {@code null} as a
 * valid state.
 */
public record UpdateProfileRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "must be at most 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "must be at most 100 characters")
        String lastName,

        @Size(max = 512, message = "must be at most 512 characters")
        String image) {
}
