package com.example.meetings.controller;

import com.example.meetings.config.SecurityConfig;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.service.AppUserDetailsService;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CalendarController.class)
@Import(SecurityConfig.class)
public class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private UserService userService;

    // ─── GET /calendar ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "john")
    void getCalendar_ReturnsCalendarViewWithModel() throws Exception {
        User user = new User("john", "john@example.com", "hash");
        Meeting meeting = new Meeting("Stand-up", null,
                Instant.parse("2026-06-06T09:00:00Z"), Instant.parse("2026-06-06T09:30:00Z"), user);
        List<MeetingParticipant> pendingInvites = List.of();

        when(userService.requireByUsername("john")).thenReturn(user);
        when(meetingService.calendarFor(user)).thenReturn(List.of(meeting));
        when(meetingService.pendingInvitesFor(user)).thenReturn(pendingInvites);

        mockMvc.perform(get("/calendar"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar"))
                .andExpect(model().attributeExists("user"))
                .andExpect(model().attributeExists("meetings"))
                .andExpect(model().attributeExists("pendingInvites"))
                .andExpect(model().attributeExists("icalHttpUrl"))
                .andExpect(model().attributeExists("icalWebcalUrl"));

        verify(userService).requireByUsername("john");
        verify(meetingService).calendarFor(user);
        verify(meetingService).pendingInvitesFor(user);
    }

    @Test
    void getCalendar_Unauthenticated_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/calendar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "alice")
    void getCalendar_IcalUrlsHaveCorrectFormat() throws Exception {
        User user = new User("alice", "alice@example.com", "hash");
        // Set a deterministic ical token via reflection for predictable assertion
        try {
            var f = User.class.getDeclaredField("icalToken");
            f.setAccessible(true);
            f.set(user, "my-ical-token");
        } catch (Exception e) { throw new RuntimeException(e); }

        when(userService.requireByUsername("alice")).thenReturn(user);
        when(meetingService.calendarFor(user)).thenReturn(List.of());
        when(meetingService.pendingInvitesFor(user)).thenReturn(List.of());

        mockMvc.perform(get("/calendar"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("icalHttpUrl",
                        "http://localhost:8080/ical/my-ical-token.ics"))
                .andExpect(model().attribute("icalWebcalUrl",
                        "webcal://localhost:8080/ical/my-ical-token.ics"));
    }
}
