package com.paynest.user.dto;

import com.paynest.user.model.User;

// What PayNest sends back when asked about a user.
// The password is excluded by NOT BEING A FIELD — the only exclusion that
// survives someone editing User next month.
public record UserResponse(
        String email,
        String firstName,
        String lastName,
        String username,
        String image) {

    // The one place a User becomes a UserResponse.
    public static UserResponse from(User user) {
        // return a new UserResponse built from user's getters.
        // image: user.getImage() returns Optional<String> — unwrap it with
        //        .orElse(null). Optional stops at the boundary.
        return new UserResponse(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getUsername(),
                user.getImage().orElse(null));
    }
}
