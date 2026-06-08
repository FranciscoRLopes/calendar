package com.example.meetings.controller;

import com.example.meetings.config.SecurityConfig;
import com.example.meetings.model.InviteStatus;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeetingController.class)
@Import(SecurityConfig.class)
public class MeetingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private UserService userService;

    // ─── GET /meetings/new ───────────────────────────────────────────────────

    @Test
    @WithMockUser
    void getProposeForm_ReturnsProposeView() throws Exception {
        mockMvc.perform(get("/meetings/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("propose"));
    }

    @Test
    void getProposeForm_Unauthenticated_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/meetings/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ─── POST /meetings/new ──────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer")
    void postPropose_Success_RedirectsToCalendar() throws Exception {
        User organizer = new User("organizer", "org@example.com", "hash");
        when(userService.requireByUsername("organizer")).thenReturn(organizer);
        when(meetingService.propose(any(), any(), any(), any(), any(), any()))
                .thenReturn(null); // return value unused by controller

        mockMvc.perform(post("/meetings/new").with(csrf())
                        .param("title", "Sprint Review")
                        .param("description", "End of sprint demo")
                        .param("start", "2026-06-10T10:00")
                        .param("end", "2026-06-10T11:00")
                        .param("invitees", "alice, bob"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        verify(meetingService).propose(eq(organizer), eq("Sprint Review"), eq("End of sprint demo"),
                any(), any(), anyList());
    }

    @Test
    @WithMockUser(username = "organizer")
    void postPropose_InvalidTime_ReturnsFormWithError() throws Exception {
        User organizer = new User("organizer", "org@example.com", "hash");
        when(userService.requireByUsername("organizer")).thenReturn(organizer);
        doThrow(new IllegalArgumentException("End time must be after start time"))
                .when(meetingService).propose(any(), any(), any(), any(), any(), any());

        mockMvc.perform(post("/meetings/new").with(csrf())
                        .param("title", "Bad Meeting")
                        .param("description", "")
                        .param("start", "2026-06-10T11:00")
                        .param("end", "2026-06-10T10:00")   // end before start
                        .param("invitees", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("propose"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attribute("title", "Bad Meeting"));
    }

    @Test
    @WithMockUser(username = "organizer")
    void postPropose_UnknownInvitee_ReturnsFormWithError() throws Exception {
        User organizer = new User("organizer", "org@example.com", "hash");
        when(userService.requireByUsername("organizer")).thenReturn(organizer);
        doThrow(new IllegalArgumentException("Unknown invitee: ghost"))
                .when(meetingService).propose(any(), any(), any(), any(), any(), any());

        mockMvc.perform(post("/meetings/new").with(csrf())
                        .param("title", "Meeting")
                        .param("description", "")
                        .param("start", "2026-06-10T10:00")
                        .param("end", "2026-06-10T11:00")
                        .param("invitees", "ghost"))
                .andExpect(status().isOk())
                .andExpect(view().name("propose"))
                .andExpect(model().attribute("error", "Unknown invitee: ghost"));
    }

    // ─── POST /meetings/{id}/respond ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "invitee")
    void postRespond_Accept_RedirectsToCalendar() throws Exception {
        User invitee = new User("invitee", "i@example.com", "hash");
        when(userService.requireByUsername("invitee")).thenReturn(invitee);

        mockMvc.perform(post("/meetings/42/respond").with(csrf())
                        .param("action", "accept"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        verify(meetingService).respond(42L, invitee, InviteStatus.ACCEPTED);
    }

    @Test
    @WithMockUser(username = "invitee")
    void postRespond_Decline_RedirectsToCalendar() throws Exception {
        User invitee = new User("invitee", "i@example.com", "hash");
        when(userService.requireByUsername("invitee")).thenReturn(invitee);

        mockMvc.perform(post("/meetings/42/respond").with(csrf())
                        .param("action", "decline"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        verify(meetingService).respond(42L, invitee, InviteStatus.DECLINED);
    }

    @Test
    void postRespond_Unauthenticated_RedirectsToLogin() throws Exception {
        mockMvc.perform(post("/meetings/42/respond").with(csrf())
                        .param("action", "accept"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
