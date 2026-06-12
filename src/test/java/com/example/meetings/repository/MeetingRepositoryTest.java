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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MeetingRepository} using an in-memory H2 database.
 *
 * The two custom JPQL queries under test — {@code findCalendarMeetings} and
 * {@code findOverlapping} — encode non-trivial business rules (participant-status
 * filtering, time-window intersection) that cannot be verified with Mockito alone.
 * Running them against a real JPA provider (Hibernate + H2) ensures the queries
 * are syntactically and semantically correct.
 */
@DataJpaTest
class MeetingRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MeetingRepository meetingRepository;

    private User organizer;
    private User participant;
    private User outsider;

    // Base timeline anchor — all test meetings are expressed relative to this.
    private static final Instant T0 = Instant.parse("2025-06-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        organizer   = em.persistAndFlush(new User("organizer",   "org@example.com",   "h1"));
        participant = em.persistAndFlush(new User("participant", "part@example.com",  "h2"));
        outsider    = em.persistAndFlush(new User("outsider",    "out@example.com",   "h3"));
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Meeting persistMeeting(String title, Instant start, Instant end, User org) {
        return em.persistAndFlush(new Meeting(title, null, start, end, org));
    }

    private void addParticipant(Meeting meeting, User user, InviteStatus status) {
        em.persistAndFlush(new MeetingParticipant(meeting, user, status));
    }

    // -----------------------------------------------------------------------
    // findCalendarMeetings — organizer visibility
    // -----------------------------------------------------------------------

    @Test
    void findCalendarMeetings_organizerSeesOwnMeeting() {
        persistMeeting("Standup", T0, T0.plus(1, ChronoUnit.HOURS), organizer);

        List<Meeting> result = meetingRepository.findCalendarMeetings(organizer);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Standup");
    }

    @Test
    void findCalendarMeetings_outsiderDoesNotSeeMeeting() {
        persistMeeting("Private", T0, T0.plus(1, ChronoUnit.HOURS), organizer);

        List<Meeting> result = meetingRepository.findCalendarMeetings(outsider);

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // findCalendarMeetings — participant status filtering
    // -----------------------------------------------------------------------

    @Test
    void findCalendarMeetings_pendingParticipantSeesMeeting() {
        Meeting m = persistMeeting("Review", T0, T0.plus(1, ChronoUnit.HOURS), organizer);
        addParticipant(m, participant, InviteStatus.PENDING);

        List<Meeting> result = meetingRepository.findCalendarMeetings(participant);

        assertThat(result).hasSize(1);
    }

    @Test
    void findCalendarMeetings_acceptedParticipantSeesMeeting() {
        Meeting m = persistMeeting("Review", T0, T0.plus(1, ChronoUnit.HOURS), organizer);
        addParticipant(m, participant, InviteStatus.ACCEPTED);

        List<Meeting> result = meetingRepository.findCalendarMeetings(participant);

        assertThat(result).hasSize(1);
    }

    @Test
    void findCalendarMeetings_declinedParticipantDoesNotSeeMeeting() {
        Meeting m = persistMeeting("Review", T0, T0.plus(1, ChronoUnit.HOURS), organizer);
        addParticipant(m, participant, InviteStatus.DECLINED);

        // A declined invite must free the slot — the meeting should disappear
        // from the participant's calendar view.
        List<Meeting> result = meetingRepository.findCalendarMeetings(participant);

        assertThat(result).isEmpty();
    }

    @Test
    void findCalendarMeetings_resultsOrderedByStartTime() {
        Instant later  = T0.plus(3, ChronoUnit.HOURS);
        Instant middle = T0.plus(1, ChronoUnit.HOURS);

        persistMeeting("C", later,  later.plus(1, ChronoUnit.HOURS),  organizer);
        persistMeeting("A", T0,     T0.plus(1, ChronoUnit.HOURS),    organizer);
        persistMeeting("B", middle, middle.plus(1, ChronoUnit.HOURS), organizer);

        List<String> titles = meetingRepository.findCalendarMeetings(organizer)
                .stream().map(Meeting::getTitle).toList();

        assertThat(titles).containsExactly("A", "B", "C");
    }

    // -----------------------------------------------------------------------
    // findOverlapping — conflict detection
    // -----------------------------------------------------------------------

    @Test
    void findOverlapping_exactlyOverlappingMeeting_isReturned() {
        // Existing meeting: 10:00 – 11:00
        persistMeeting("Existing", T0, T0.plus(1, ChronoUnit.HOURS), organizer);

        // New candidate: 10:30 – 11:30 — overlaps with the existing one.
        List<Meeting> conflicts = meetingRepository.findOverlapping(
                organizer,
                T0.plus(30, ChronoUnit.MINUTES),
                T0.plus(90, ChronoUnit.MINUTES));

        assertThat(conflicts).hasSize(1);
    }

    @Test
    void findOverlapping_adjacentMeeting_isNotReturned() {
        // Existing meeting ends exactly when the new one begins — no overlap.
        persistMeeting("Before", T0, T0.plus(1, ChronoUnit.HOURS), organizer);

        List<Meeting> conflicts = meetingRepository.findOverlapping(
                organizer,
                T0.plus(1, ChronoUnit.HOURS),
                T0.plus(2, ChronoUnit.HOURS));

        assertThat(conflicts).isEmpty();
    }

    @Test
    void findOverlapping_noMeetingsInWindow_returnsEmpty() {
        persistMeeting("Morning", T0, T0.plus(1, ChronoUnit.HOURS), organizer);

        // Query for an afternoon slot — no conflict expected.
        List<Meeting> conflicts = meetingRepository.findOverlapping(
                organizer,
                T0.plus(5, ChronoUnit.HOURS),
                T0.plus(6, ChronoUnit.HOURS));

        assertThat(conflicts).isEmpty();
    }

    @Test
    void findOverlapping_declinedParticipantSlotIsNotBlocked() {
        // If a participant declined, the slot should be free for them to accept
        // other meetings at the same time.
        Meeting m = persistMeeting("OriginalMeeting", T0, T0.plus(1, ChronoUnit.HOURS), organizer);
        addParticipant(m, participant, InviteStatus.DECLINED);

        List<Meeting> conflicts = meetingRepository.findOverlapping(
                participant,
                T0,
                T0.plus(1, ChronoUnit.HOURS));

        assertThat(conflicts).isEmpty();
    }

    @Test
    void findOverlapping_outsiderHasNoConflict() {
        persistMeeting("Internal", T0, T0.plus(1, ChronoUnit.HOURS), organizer);

        // The outsider is neither organizer nor participant, so no conflict.
        List<Meeting> conflicts = meetingRepository.findOverlapping(
                outsider, T0, T0.plus(1, ChronoUnit.HOURS));

        assertThat(conflicts).isEmpty();
    }
}
