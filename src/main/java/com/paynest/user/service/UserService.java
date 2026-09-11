package com.paynest.user.service;

import com.paynest.user.exception.DuplicateEmailException;
import com.paynest.user.model.User;
import com.paynest.user.repository.InMemoryUserRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;

@Service
public class UserService {

    private final InMemoryUserRepository repository;

    public UserService(InMemoryUserRepository repository) {
        this.repository = repository;
    }

    public User register(User user) {
        if (repository.findByEmail(user.getEmail()).isPresent()) {
            throw new DuplicateEmailException(user.getEmail());
        }
        return repository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    public User getByEmail(String email) {
        return repository.getByEmail(email);
    }

    public void deleteByEmail(String email) {
        repository.deleteByEmail(email);
    }

    public Collection<User> findAll() {
        return repository.findAll();
    }

    public long count() {
        return repository.count();
    }
}
