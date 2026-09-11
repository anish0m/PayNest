package com.paynest.user.repository;

import com.paynest.user.exception.DuplicateEmailException;
import com.paynest.user.exception.UserNotFoundException;
import com.paynest.user.model.User;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryUserRepository {

    private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();

    public User save(User user) {
        if (usersByEmail.containsKey(user.getEmail())) {
            throw new DuplicateEmailException(user.getEmail());
        }
        usersByEmail.put(user.getEmail(), user);
        return user;
    }

    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(usersByEmail.get(email));
    }

    public User getByEmail(String email) {
        return findByEmail(email).orElseThrow(() -> new UserNotFoundException(email));
    }

    public void deleteByEmail(String email) {
        if (usersByEmail.remove(email) == null) {
            throw new UserNotFoundException(email);
        }
    }

    public Collection<User> findAll() {
        return List.copyOf(usersByEmail.values());
    }

    public long count() {
        return usersByEmail.size();
    }
}
