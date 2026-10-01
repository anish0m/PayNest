package com.paynest.user.service;

import com.paynest.user.exception.DuplicateEmailException;
import com.paynest.user.model.User;
import com.paynest.user.repository.UserRepository;
import com.paynest.user.dto.CreateUserRequest;
import com.paynest.user.dto.UpdateProfileRequest;
import com.paynest.user.dto.LoginRequest;
import com.paynest.user.exception.InvalidCredentialsException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;

/**
 * Business rules for the user domain — the layer between "signup arrived"
 * and "row exists in storage."
 */
@Service
public class UserService {

    private final UserRepository repository;

    /**
     * Injected as the {@link PasswordEncoder} interface, not
     * {@code BCryptPasswordEncoder} — this class must know that passwords
     * are hashed, and must not know how. A move to argon2 then changes only
     * {@code SecurityConfig}.
     */
    private final PasswordEncoder passwordEncoder;

    private static final String DUMMY_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5L4mfP/EMDT1lqGKgnJKpgJH4pOb2";

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user.
     *
     * <p>Takes a {@link CreateUserRequest} rather than a {@link User} because
     * {@code register(User)} could not distinguish a request-built User
     * (plaintext password, must be hashed) from a Hibernate-loaded one (a
     * hash, must not be hashed again). A {@code CreateUserRequest} is only
     * ever built from a request body, so the question stops being askable.
     *
     * <p>The duplicate check is policy, not the guarantee — it loses the same
     * race as the storage layer's UNIQUE constraint, which is what actually
     * prevents the duplicate. This check exists for a clean error message.
     *
     * <p>Hashing happens here and nowhere else: not in the controller
     * (hashing is not a transport concern — any future non-HTTP caller would
     * silently store plaintext), not in {@code User} (the model would need a
     * {@code PasswordEncoder}, and could no longer be built in a plain unit
     * test). Here, because this is the one method that knows a signup is
     * happening — exactly when a plaintext password exists, and the last
     * moment it may.
     */
    public User register(CreateUserRequest request) {
        if (repository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException(request.email());
        }

        String hash = passwordEncoder.encode(request.password());

        return repository.save(new User(
                request.firstName(),
                request.lastName(),
                request.email(),
                hash));
    }

    /**
     * Edits an existing user's profile.
     *
     * <p>Loads the user via {@code getByEmail} (throws {@code UserNotFoundException}
     * if absent — a caller naming a specific email cannot continue without that
     * row existing), mutates it via the model's validating setters rather than
     * constructing a new {@code User}, then persists via {@code repository.update},
     * not {@code save} — {@code save} means INSERT and is wrong for an existing row.
     *
     * <p>{@code request.image()} may be {@code null}: {@code UpdateProfileRequest}
     * deliberately omits {@code @NotBlank} on it, since leaving {@code image} out of
     * the JSON body is how a caller expresses "clear it," and {@code User.setImage}
     * already treats {@code null} as valid.
     */
    @Transactional
    public User updateProfile(String email, UpdateProfileRequest request) {
        User user = repository.getByEmail(email);

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setImage(request.image());

        return repository.update(user);
    }

    public User login(LoginRequest request) {
        Optional<User> maybeUser = repository.findByEmail(request.email());

        if (maybeUser.isEmpty()) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw new InvalidCredentialsException();
        }

        User user = maybeUser.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return user;
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
