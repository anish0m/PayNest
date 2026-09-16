package com.paynest.user.repository;

import com.paynest.user.model.User;

import java.util.Collection;
import java.util.Optional;

public interface UserRepository {
    User save(User user);

    Optional<User> findByEmail(String email);

    User getByEmail(String email);

    void deleteByEmail(String email);

    Collection<User> findAll();

    long count();
}