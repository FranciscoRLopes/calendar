package com.example.meetings.repository;

import com.example.meetings.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for {@link UserRepository} using an in-memory H2 database.
 *
 * {@code @DataJpaTest} bootstraps only the JPA slice of the application context,
 * replacing the production DataSource with an embedded H2 instance and wrapping
 * each test in a transaction that is rolled back after the test completes.
 * This keeps tests isolated from one another without requiring manual cleanup.
 */
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserRepository userRepository;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = em.persistAndFlush(new User("alice", "alice@example.com", "hash_alice"));
    }

    // -----------------------------------------------------------------------
    // findByUsername
    // -----------------------------------------------------------------------

    @Test
    void findByUsername_existingUser_returnsUser() {
        Optional<User> result = userRepository.findByUsername("alice");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void findByUsername_unknownUsername_returnsEmpty() {
        Optional<User> result = userRepository.findByUsername("nobody");

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // findByIcalToken
    // -----------------------------------------------------------------------

    @Test
    void findByIcalToken_validToken_returnsUser() {
        String token = alice.getIcalToken();

        Optional<User> result = userRepository.findByIcalToken(token);

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("alice");
    }

    @Test
    void findByIcalToken_unknownToken_returnsEmpty() {
        Optional<User> result = userRepository.findByIcalToken("non-existent-token");

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // existsByUsername
    // -----------------------------------------------------------------------

    @Test
    void existsByUsername_existingUser_returnsTrue() {
        assertThat(userRepository.existsByUsername("alice")).isTrue();
    }

    @Test
    void existsByUsername_unknownUser_returnsFalse() {
        assertThat(userRepository.existsByUsername("ghost")).isFalse();
    }

    // -----------------------------------------------------------------------
    // Uniqueness constraints
    // -----------------------------------------------------------------------

    @Test
    void savingDuplicateUsername_throwsException() {
        User duplicate = new User("alice", "other@example.com", "hash2");

        // The unique constraint on `username` must prevent this from being persisted.
        assertThrows(Exception.class, () -> em.persistAndFlush(duplicate));
    }

    @Test
    void savingDuplicateIcalToken_throwsException() {
        // Craft a user whose icalToken is forcibly set to alice's token via reflection,
        // simulating the (astronomically unlikely) UUID collision path.
        User clone = new User("bob", "bob@example.com", "hash_bob");
        try {
            var field = User.class.getDeclaredField("icalToken");
            field.setAccessible(true);
            field.set(clone, alice.getIcalToken());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertThrows(Exception.class, () -> em.persistAndFlush(clone));
    }
}
