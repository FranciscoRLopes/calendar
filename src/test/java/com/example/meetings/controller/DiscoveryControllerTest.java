package com.example.meetings.controller;

import com.example.meetings.config.SecurityConfig;
import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.DiscoveryService;
import com.example.meetings.discover.EventProvider;
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

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DiscoveryController.class)
@Import(SecurityConfig.class)
public class DiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private DiscoveryService discoveryService;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private UserService userService;

    // ─── GET /discover ───────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void getDiscover_NoQuery_ReturnsEmptyResults() throws Exception {
        EventProvider provider = mock(EventProvider.class);
        when(provider.isConfigured()).thenReturn(true);
        when(discoveryService.providers()).thenReturn(List.of(provider));

        mockMvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"))
                .andExpect(model().attribute("q", ""))
                .andExpect(model().attribute("results", empty()))
                .andExpect(model().attribute("anyConfigured", true));

        verify(discoveryService, never()).search(any());
    }

    @Test
    @WithMockUser
    void getDiscover_WithQuery_ReturnsSearchResults() throws Exception {
        EventProvider provider = mock(EventProvider.class);
        when(provider.isConfigured()).thenReturn(true);
        when(discoveryService.providers()).thenReturn(List.of(provider));

        DiscoveredEvent event = new DiscoveredEvent(
                "Ticketmaster", "tm1", "Rock Festival", "Desc",
                Instant.parse("2026-08-01T18:00:00Z"), null, "http://tm.com/1", "Arena");
        when(discoveryService.search("rock")).thenReturn(List.of(event));

        mockMvc.perform(get("/discover").param("q", "rock"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"))
                .andExpect(model().attribute("q", "rock"))
                .andExpect(model().attribute("results", hasSize(1)));

        verify(discoveryService).search("rock");
    }

    @Test
    @WithMockUser
    void getDiscover_NoConfiguredProviders_ReturnsEmptyResultsWithoutSearching() throws Exception {
        EventProvider unconfiguredProvider = mock(EventProvider.class);
        when(unconfiguredProvider.isConfigured()).thenReturn(false);
        when(discoveryService.providers()).thenReturn(List.of(unconfiguredProvider));

        mockMvc.perform(get("/discover").param("q", "rock"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("anyConfigured", false))
                .andExpect(model().attribute("results", empty()));

        verify(discoveryService, never()).search(any());
    }

    @Test
    void getDiscover_Unauthenticated_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/discover"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ─── POST /discover/copy ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    void postCopy_Success_RedirectsToCalendar() throws Exception {
        User alice = new User("alice", "alice@example.com", "hash");
        when(userService.requireByUsername("alice")).thenReturn(alice);

        mockMvc.perform(post("/discover/copy").with(csrf())
                        .param("source", "Ticketmaster")
                        .param("externalId", "tm-123")
                        .param("title", "Rock Concert")
                        .param("description", "Amazing show")
                        .param("start", "2026-08-01T18:00:00Z")
                        .param("end", "2026-08-01T21:00:00Z")
                        .param("url", "http://tm.com/1")
                        .param("venue", "Arena"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        verify(meetingService).copyFromDiscovered(eq(alice), any());
    }

    @Test
    @WithMockUser(username = "alice")
    void postCopy_WithoutEndTime_RedirectsToCalendar() throws Exception {
        User alice = new User("alice", "alice@example.com", "hash");
        when(userService.requireByUsername("alice")).thenReturn(alice);

        mockMvc.perform(post("/discover/copy").with(csrf())
                        .param("source", "SeatGeek")
                        .param("externalId", "sg-456")
                        .param("title", "Football Match")
                        .param("start", "2026-09-15T20:00:00Z")
                        // no end param
                        .param("venue", "Estádio"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        verify(meetingService).copyFromDiscovered(eq(alice), argThat(e -> e.end() == null));
    }

    @Test
    void postCopy_Unauthenticated_RedirectsToLogin() throws Exception {
        mockMvc.perform(post("/discover/copy").with(csrf())
                        .param("source", "X")
                        .param("externalId", "1")
                        .param("title", "T")
                        .param("start", "2026-08-01T18:00:00Z"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
