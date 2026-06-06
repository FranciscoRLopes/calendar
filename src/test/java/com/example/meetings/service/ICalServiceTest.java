package com.example.meetings.service;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ICalServiceTest {

    private ICalService iCalService;
    private User owner;

    @BeforeEach
    void setUp() {
        iCalService = new ICalService();
        owner = new User("owner_user", "owner@example.com", "hash");
        setId(owner, 1L);
    }

    @Test
    void render_EmptyMeetings() {
        // Arrange
        List<Meeting> meetings = new ArrayList<>();

        // Act
        String result = iCalService.render(owner, meetings);

        // Assert
        assertTrue(result.contains("BEGIN:VCALENDAR"));
        assertTrue(result.contains("VERSION:2.0"));
        assertTrue(result.contains("PRODID:-//meetings-app//EN"));
        assertTrue(result.contains("CALSCALE:GREGORIAN"));
        assertTrue(result.contains("METHOD:PUBLISH"));
        assertTrue(result.contains("X-WR-CALNAME:owner_user's meetings"));
        assertTrue(result.contains("END:VCALENDAR"));
        assertFalse(result.contains("BEGIN:VEVENT"));
    }

    @Test
    void render_WithMeetingsAndEscaping() {
        // Arrange
        User organizer = new User("org_user", "org@example.com", "hash");
        setId(organizer, 2L);

        User invitee = new User("invitee_user", "invitee@example.com", "hash");
        setId(invitee, 3L);

        Meeting meeting1 = new Meeting(
                "Project Discussion; Meeting, \"Fun\"",
                "This is a description with backslash \\ and semicolon ; and comma ,",
                Instant.parse("2026-06-06T10:00:00Z"),
                Instant.parse("2026-06-06T11:00:00Z"),
                organizer
        );
        setId(meeting1, 101L);

        // Add participants
        MeetingParticipant p1 = new MeetingParticipant(meeting1, organizer, InviteStatus.ACCEPTED);
        MeetingParticipant p2 = new MeetingParticipant(meeting1, invitee, InviteStatus.PENDING);
        meeting1.addParticipant(p1);
        meeting1.addParticipant(p2);

        List<Meeting> meetings = List.of(meeting1);

        // Act
        String result = iCalService.render(owner, meetings);

        // Assert
        assertTrue(result.contains("BEGIN:VCALENDAR"));
        assertTrue(result.contains("BEGIN:VEVENT"));
        assertTrue(result.contains("UID:meeting-101@meetings-app"));
        assertTrue(result.contains("DTSTART:20260606T100000Z"));
        assertTrue(result.contains("DTEND:20260606T110000Z"));
        
        // Check escaping of SUMMARY and DESCRIPTION
        assertTrue(result.contains("SUMMARY:Project Discussion\\; Meeting\\, \"Fun\""));
        assertTrue(result.contains("DESCRIPTION:This is a description with backslash \\\\ and semicolon \\; and comma \\,"));

        // Check organizer and attendees rendering
        assertTrue(result.contains("ORGANIZER;CN=org_user:mailto:org@example.com"));
        assertTrue(result.contains("ATTENDEE;CN=org_user;PARTSTAT=ACCEPTED:mailto:org@example.com"));
        assertTrue(result.contains("ATTENDEE;CN=invitee_user;PARTSTAT=NEEDS-ACTION:mailto:invitee@example.com"));
        
        // Confirmed status (not confirmed because one participant is PENDING)
        assertFalse(meeting1.isConfirmed());
        assertTrue(result.contains("STATUS:TENTATIVE"));

        assertTrue(result.contains("END:VEVENT"));
        assertTrue(result.contains("END:VCALENDAR"));
    }

    @Test
    void render_ConfirmedMeeting() {
        // Arrange
        User organizer = new User("org_user", "org@example.com", "hash");
        setId(organizer, 2L);

        Meeting meeting = new Meeting(
                "Confirmed Event",
                "Description",
                Instant.parse("2026-06-06T12:00:00Z"),
                Instant.parse("2026-06-06T13:00:00Z"),
                organizer
        );
        setId(meeting, 102L);

        // All participants accept
        MeetingParticipant p1 = new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED);
        meeting.addParticipant(p1);

        List<Meeting> meetings = List.of(meeting);

        // Act
        String result = iCalService.render(owner, meetings);

        // Assert
        assertTrue(meeting.isConfirmed());
        assertTrue(result.contains("STATUS:CONFIRMED"));
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
