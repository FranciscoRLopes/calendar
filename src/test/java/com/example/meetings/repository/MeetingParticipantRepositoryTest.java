package com.example.meetings.repository;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for {@link MeetingParticipantRepository} using an in-memory H2 database.
 *
 * The two derived queries — {@code findByUserAndStatus} and
 * {@code findByMeetingIdAndUserId} — are tested against actual JPA persistence to
 * confirm that Spring Data generates the correct SQL and that the unique constraint
 * on {@code (meeting_id, user_id)} is enforced at the database level.
 */
@DataJpaTest
class MeetingParticipantRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MeetingParticipantRepository participantRepository;

    private User alice;
    private User bob;
    private Meeting meeting1;
    private Meeting meeting2;

    private static final Instant T0 = Instant.parse("2025-06-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        alice    = em.persistAndFlush(new User("alice", "alice@example.com", "h1"));
        bob      = em.persistAndFlush(new User("bob",   "bob@example.com",   "h2"));
        meeting1 = em.persistAndFlush(new Meeting("Sprint Planning", null,
                T0, T0.plus(1, ChronoUnit.HOURS), alice));
        meeting2 = em.persistAndFlush(new Meeting("Retro", null,
                T0.plus(2, ChronoUnit.HOURS), T0.plus(3, ChronoUnit.HOURS), alice));
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private MeetingParticipant persist(Meeting m, User u, InviteStatus s) {
        return em.persistAndFlush(new MeetingParticipant(m, u, s));
    }

    // -----------------------------------------------------------------------
    // findByUserAndStatus
    // -----------------------------------------------------------------------

    @Test
    void findByUserAndStatus_returnsPendingInvitesForUser() {
        persist(meeting1, bob, InviteStatus.PENDING);
        persist(meeting2, bob, InviteStatus.ACCEPTED); // should NOT appear

        List<MeetingParticipant> result =
                participantRepository.findByUserAndStatus(bob, InviteStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMeeting().getTitle()).isEqualTo("Sprint Planning");
    }

    @Test
    void findByUserAndStatus_noMatchingStatus_returnsEmpty() {
        persist(meeting1, bob, InviteStatus.ACCEPTED);

        List<MeetingParticipant> result =
                participantRepository.findByUserAndStatus(bob, InviteStatus.DECLINED);

        assertThat(result).isEmpty();
    }

    @Test
    void findByUserAndStatus_multipleMatchingRecords_allReturned() {
        persist(meeting1, bob, InviteStatus.ACCEPTED);
        persist(meeting2, bob, InviteStatus.ACCEPTED);

        List<MeetingParticipant> result =
                participantRepository.findByUserAndStatus(bob, InviteStatus.ACCEPTED);

        assertThat(result).hasSize(2);
    }

    @Test
    void findByUserAndStatus_doesNotReturnOtherUsersRecords() {
        persist(meeting1, alice, InviteStatus.PENDING);
        persist(meeting1, bob,   InviteStatus.PENDING);

        List<MeetingParticipant> result =
                participantRepository.findByUserAndStatus(alice, InviteStatus.PENDING);

        // Only alice's record should appear, not bob's.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUser().getUsername()).isEqualTo("alice");
    }

    // -----------------------------------------------------------------------
    // findByMeetingIdAndUserId
    // -----------------------------------------------------------------------

    @Test
    void findByMeetingIdAndUserId_existingRecord_returnsParticipant() {
        MeetingParticipant mp = persist(meeting1, bob, InviteStatus.PENDING);

        Optional<MeetingParticipant> result =
                participantRepository.findByMeetingIdAndUserId(meeting1.getId(), bob.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(mp.getId());
        assertThat(result.get().getStatus()).isEqualTo(InviteStatus.PENDING);
    }

    @Test
    void findByMeetingIdAndUserId_wrongMeetingId_returnsEmpty() {
        persist(meeting1, bob, InviteStatus.PENDING);

        Optional<MeetingParticipant> result =
                participantRepository.findByMeetingIdAndUserId(meeting2.getId(), bob.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByMeetingIdAndUserId_wrongUserId_returnsEmpty() {
        persist(meeting1, bob, InviteStatus.PENDING);

        Optional<MeetingParticipant> result =
                participantRepository.findByMeetingIdAndUserId(meeting1.getId(), alice.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByMeetingIdAndUserId_nonExistentIds_returnsEmpty() {
        Optional<MeetingParticipant> result =
                participantRepository.findByMeetingIdAndUserId(999L, 999L);

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Unique constraint — (meeting_id, user_id)
    // -----------------------------------------------------------------------

    @Test
    void addingSameUserToSameMeetingTwice_throwsException() {
        persist(meeting1, bob, InviteStatus.PENDING);

        // The @UniqueConstraint on (meeting_id, user_id) must reject a duplicate entry.
        assertThrows(Exception.class,
                () -> persist(meeting1, bob, InviteStatus.ACCEPTED));
    }

    @Test
    void sameUserCanParticipateInDifferentMeetings() {
        persist(meeting1, bob, InviteStatus.PENDING);
        persist(meeting2, bob, InviteStatus.PENDING); // different meeting — must succeed

        List<MeetingParticipant> result =
                participantRepository.findByUserAndStatus(bob, InviteStatus.PENDING);

        assertThat(result).hasSize(2);
    }

    // -----------------------------------------------------------------------
    // Status mutation persistence
    // -----------------------------------------------------------------------

    @Test
    void updatingStatus_persistsCorrectly() {
        MeetingParticipant mp = persist(meeting1, bob, InviteStatus.PENDING);

        mp.setStatus(InviteStatus.ACCEPTED);
        em.persistAndFlush(mp);
        em.clear(); // evict from first-level cache to force a DB read

        Optional<MeetingParticipant> reloaded =
                participantRepository.findByMeetingIdAndUserId(meeting1.getId(), bob.getId());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(InviteStatus.ACCEPTED);
    }
}
