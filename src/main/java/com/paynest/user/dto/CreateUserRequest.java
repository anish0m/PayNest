package com.paynest.user.dto;

// A record: a name for a group of values, no behaviour.
// Java generates the constructor, accessors, equals, hashCode, toString.
// Fields are final. Jackson understands records natively.
//
// Field order follows YOUR User constructor: firstName, lastName, email, password.
public record CreateUserRequest(
        String firstName,
        String lastName,
        String email,
        String password) {
}

