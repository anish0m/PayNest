package com.paynest.user.repository;

import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.paynest.user.model.User;
import com.paynest.user.exception.DuplicateEmailException;
import com.paynest.user.exception.UserNotFoundException;

@Repository
@Profile("jpa")
public class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository users;

    private final EntityManager entityManager;

    public JpaUserRepository(SpringDataUserRepository users, EntityManager entityManager) {
        this.users = users;
        this.entityManager = entityManager;
    }

    private boolean isDuplicateEmail(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null
                && (message.contains("users_email_key") || message.contains("(email)"));
    }

    @Override
    @Transactional
    public User save(User user) {
        try {
            User saved = users.save(user);

            users.flush();

            entityManager.refresh(saved);

            return saved;
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateEmail(e)) {
                throw new DuplicateEmailException(user.getEmail());
            }

            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return users.findByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return users.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    @Override
    @Transactional
    public void deleteByEmail(String email) {
        if (users.deleteByEmail(email) == 0) {
            throw new UserNotFoundException(email);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<User> findAll() {
        return users.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return users.count();
    }
}
