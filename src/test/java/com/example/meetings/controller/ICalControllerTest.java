package com.example.meetings.controller;

import com.example.meetings.model.Meeting;
import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.ICalService;
import com.example.meetings.service.MeetingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ICalController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ICalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private ICalService icalService;

    // ─── GET /ical/{token}.ics ───────────────────────────────────────────────

    @Test
    void getIcalFeed_ValidToken_ReturnsCalendarContent() throws Exception {
        String token = "valid-token-abc";
        User user = new User("alice", "alice@example.com", "hash");
        Meeting meeting = new Meeting("Sprint Review", "Demo",
                Instant.parse("2026-06-06T09:00:00Z"), Instant.parse("2026-06-06T10:00:00Z"), user);

        when(userRepository.findByIcalToken(token)).thenReturn(Optional.of(user));
        when(meetingService.calendarFor(user)).thenReturn(List.of(meeting));
        when(icalService.render(user, List.of(meeting)))
                .thenReturn("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");

        mockMvc.perform(get("/ical/{token}.ics", token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(header().string("Content-Disposition",
                        "inline; filename=\"meetings.ics\""))
                .andExpect(content().string("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"));

        verify(userRepository).findByIcalToken(token);
        verify(meetingService).calendarFor(user);
        verify(icalService).render(user, List.of(meeting));
    }

    @Test
    void getIcalFeed_InvalidToken_Returns404() throws Exception {
        String token = "invalid-token-xyz";
        when(userRepository.findByIcalToken(token)).thenReturn(Optional.empty());

        mockMvc.perform(get("/ical/{token}.ics", token))
                .andExpect(status().isNotFound());

        verify(userRepository).findByIcalToken(token);
        verifyNoInteractions(meetingService, icalService);
    }

    @Test
    void getIcalFeed_IsPublicEndpoint_DoesNotRequireAuthentication() throws Exception {
        // The /ical/** endpoint is publicly accessible (no auth required).
        // An anonymous request with a valid token must succeed without a login redirect.
        String token = "public-token";
        User user = new User("bob", "bob@example.com", "hash");

        when(userRepository.findByIcalToken(token)).thenReturn(Optional.of(user));
        when(meetingService.calendarFor(user)).thenReturn(List.of());
        when(icalService.render(user, List.of())).thenReturn("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");

        // No @WithMockUser — request is unauthenticated
        mockMvc.perform(get("/ical/{token}.ics", token))
                .andExpect(status().isOk());
    }
}
