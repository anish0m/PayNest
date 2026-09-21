package com.paynest.user.repository;

import com.paynest.user.model.User;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;


public interface SpringDataUserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    long deleteByEmail(String email);

    boolean existsByEmail(String email);
}
