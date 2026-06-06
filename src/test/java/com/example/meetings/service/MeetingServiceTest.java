package com.example.meetings.service;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MeetingServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingParticipantRepository participantRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MeetingService meetingService;

    private User organizer;
    private User invitee1;
    private User invitee2;

    @BeforeEach
    void setUp() {
        organizer = new User("organizer", "org@example.com", "hash");
        setId(organizer, 1L);
        invitee1 = new User("invitee1", "i1@example.com", "hash");
        setId(invitee1, 2L);
        invitee2 = new User("invitee2", "i2@example.com", "hash");
        setId(invitee2, 3L);
    }

    @Test
    void propose_Success() {
        // Arrange
        Instant start = Instant.parse("2026-06-06T10:00:00Z");
        Instant end = Instant.parse("2026-06-06T11:00:00Z");
        List<String> usernames = Arrays.asList("invitee1", " invitee2 ", "invitee1", "organizer", "", null);

        when(userRepository.findByUsername("invitee1")).thenReturn(Optional.of(invitee1));
        when(userRepository.findByUsername("invitee2")).thenReturn(Optional.of(invitee2));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Meeting meeting = meetingService.propose(organizer, "Design review", "Desc", start, end, usernames);

        // Assert
        assertNotNull(meeting);
        assertEquals("Design review", meeting.getTitle());
        assertEquals("Desc", meeting.getDescription());
        assertEquals(start, meeting.getStartTime());
        assertEquals(end, meeting.getEndTime());
        assertEquals(organizer, meeting.getOrganizer());

        // Participants check (organizer + invitee1 + invitee2)
        assertEquals(3, meeting.getParticipants().size());
        
        // Organizer participant should be ACCEPTED
        MeetingParticipant organizerPart = meeting.getParticipants().stream()
                .filter(p -> p.getUser().equals(organizer))
                .findFirst().orElseThrow();
        assertEquals(InviteStatus.ACCEPTED, organizerPart.getStatus());

        // Invitee1 participant should be PENDING
        MeetingParticipant invitee1Part = meeting.getParticipants().stream()
                .filter(p -> p.getUser().equals(invitee1))
                .findFirst().orElseThrow();
        assertEquals(InviteStatus.PENDING, invitee1Part.getStatus());

        // Invitee2 participant should be PENDING
        MeetingParticipant invitee2Part = meeting.getParticipants().stream()
                .filter(p -> p.getUser().equals(invitee2))
                .findFirst().orElseThrow();
        assertEquals(InviteStatus.PENDING, invitee2Part.getStatus());

        verify(userRepository, times(1)).findByUsername("invitee1");
        verify(userRepository, times(1)).findByUsername("invitee2");
        verify(meetingRepository, times(1)).save(any(Meeting.class));
    }

    @Test
    void propose_EndTimeNotAfterStart_ThrowsException() {
        // Arrange
        Instant start = Instant.parse("2026-06-06T11:00:00Z");
        Instant end = Instant.parse("2026-06-06T10:00:00Z");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            meetingService.propose(organizer, "Title", "Desc", start, end, Collections.emptyList());
        });
        verifyNoInteractions(userRepository, meetingRepository);
    }

    @Test
    void propose_UnknownInvitee_ThrowsException() {
        // Arrange
        Instant start = Instant.parse("2026-06-06T10:00:00Z");
        Instant end = Instant.parse("2026-06-06T11:00:00Z");
        List<String> usernames = List.of("unknown_user");

        when(userRepository.findByUsername("unknown_user")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            meetingService.propose(organizer, "Title", "Desc", start, end, usernames);
        });
        verify(userRepository, times(1)).findByUsername("unknown_user");
        verify(meetingRepository, never()).save(any(Meeting.class));
    }

    @Test
    void calendarFor_Success() {
        // Arrange
        List<Meeting> expectedMeetings = List.of(new Meeting("Title", "Desc", Instant.now(), Instant.now().plus(Duration.ofHours(1)), organizer));
        when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(expectedMeetings);

        // Act
        List<Meeting> result = meetingService.calendarFor(organizer);

        // Assert
        assertEquals(expectedMeetings, result);
        verify(meetingRepository, times(1)).findCalendarMeetings(organizer);
    }

    @Test
    void pendingInvitesFor_Success() {
        // Arrange
        List<MeetingParticipant> expectedInvites = List.of(new MeetingParticipant(null, organizer, InviteStatus.PENDING));
        when(participantRepository.findByUserAndStatus(organizer, InviteStatus.PENDING)).thenReturn(expectedInvites);

        // Act
        List<MeetingParticipant> result = meetingService.pendingInvitesFor(organizer);

        // Assert
        assertEquals(expectedInvites, result);
        verify(participantRepository, times(1)).findByUserAndStatus(organizer, InviteStatus.PENDING);
    }

    @Test
    void respond_Accepted_Success() {
        // Arrange
        Meeting meeting = new Meeting("Title", "Desc", Instant.now(), Instant.now().plus(Duration.ofHours(1)), organizer);
        setId(meeting, 10L);
        MeetingParticipant participant = new MeetingParticipant(meeting, invitee1, InviteStatus.PENDING);
        when(participantRepository.findByMeetingIdAndUserId(10L, 2L)).thenReturn(Optional.of(participant));

        // Act
        meetingService.respond(10L, invitee1, InviteStatus.ACCEPTED);

        // Assert
        assertEquals(InviteStatus.ACCEPTED, participant.getStatus());
        verify(participantRepository, times(1)).findByMeetingIdAndUserId(10L, 2L);
    }

    @Test
    void respond_Declined_Success() {
        // Arrange
        Meeting meeting = new Meeting("Title", "Desc", Instant.now(), Instant.now().plus(Duration.ofHours(1)), organizer);
        setId(meeting, 10L);
        MeetingParticipant participant = new MeetingParticipant(meeting, invitee1, InviteStatus.PENDING);
        when(participantRepository.findByMeetingIdAndUserId(10L, 2L)).thenReturn(Optional.of(participant));

        // Act
        meetingService.respond(10L, invitee1, InviteStatus.DECLINED);

        // Assert
        assertEquals(InviteStatus.DECLINED, participant.getStatus());
        verify(participantRepository, times(1)).findByMeetingIdAndUserId(10L, 2L);
    }

    @Test
    void respond_InvalidStatus_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            meetingService.respond(10L, invitee1, InviteStatus.PENDING);
        });
        verifyNoInteractions(participantRepository);
    }

    @Test
    void respond_NoInviteFound_ThrowsException() {
        // Arrange
        when(participantRepository.findByMeetingIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            meetingService.respond(10L, invitee1, InviteStatus.ACCEPTED);
        });
        verify(participantRepository, times(1)).findByMeetingIdAndUserId(10L, 2L);
    }

    @Test
    void copyFromDiscovered_WithEndTime_Success() {
        // Arrange
        Instant start = Instant.parse("2026-06-06T10:00:00Z");
        Instant end = Instant.parse("2026-06-06T12:00:00Z");
        DiscoveredEvent event = new DiscoveredEvent(
                "Ticketmaster", "ext123", "Concert", "Rock Concert", start, end, "http://ticketmaster.com", "Venue"
        );

        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        // Assert
        assertNotNull(result);
        assertEquals("Concert", result.getTitle());
        assertEquals("Rock Concert\n\nVenue: Venue\nSource: Ticketmaster (http://ticketmaster.com)", result.getDescription());
        assertEquals(start, result.getStartTime());
        assertEquals(end, result.getEndTime());
        assertEquals(organizer, result.getOrganizer());
        
        assertEquals(1, result.getParticipants().size());
        MeetingParticipant part = result.getParticipants().iterator().next();
        assertEquals(organizer, part.getUser());
        assertEquals(InviteStatus.ACCEPTED, part.getStatus());

        verify(meetingRepository, times(1)).save(any(Meeting.class));
    }

    @Test
    void copyFromDiscovered_WithoutEndTime_Success() {
        // Arrange
        Instant start = Instant.parse("2026-06-06T10:00:00Z");
        DiscoveredEvent event = new DiscoveredEvent(
                "SeatGeek", "ext456", "Match", null, start, null, null, "Stadium"
        );

        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        // Assert
        assertNotNull(result);
        assertEquals("Match", result.getTitle());
        assertEquals("Venue: Stadium\nSource: SeatGeek", result.getDescription());
        assertEquals(start, result.getStartTime());
        assertEquals(start.plus(Duration.ofHours(2)), result.getEndTime()); // Default 2 hours
        assertEquals(organizer, result.getOrganizer());

        verify(meetingRepository, times(1)).save(any(Meeting.class));
    }

    @Test
    void calendarForIcalToken_Success() {
        // Arrange
        String token = "ical-token-abc";
        when(userRepository.findByIcalToken(token)).thenReturn(Optional.of(organizer));
        List<Meeting> expectedMeetings = List.of(new Meeting("Title", "Desc", Instant.now(), Instant.now().plus(Duration.ofHours(1)), organizer));
        when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(expectedMeetings);

        // Act
        List<Meeting> result = meetingService.calendarForIcalToken(token);

        // Assert
        assertNotNull(result);
        assertEquals(expectedMeetings.size(), result.size());
        assertEquals(expectedMeetings.get(0), result.get(0));

        verify(userRepository, times(1)).findByIcalToken(token);
        verify(meetingRepository, times(1)).findCalendarMeetings(organizer);
    }

    @Test
    void calendarForIcalToken_InvalidToken_ThrowsException() {
        // Arrange
        String token = "invalid-token";
        when(userRepository.findByIcalToken(token)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            meetingService.calendarForIcalToken(token);
        });

        verify(userRepository, times(1)).findByIcalToken(token);
        verifyNoInteractions(meetingRepository);
    }

    private void setId(Object object, Long id) {
        try {
            java.lang.reflect.Field field = object.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(object, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
