package com.example.meetings.controller;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Status;
import java.util.List;
import net.fortuna.ical4j.model.property.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.StringReader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the iCal feed at the REST API level,
 * focused on RFC 5545 compliance using iCal4j as an independent validator.
 *
 * Unlike ICalControllerTest (which uses @WebMvcTest with mocks and verifies
 * controller behaviour), this class runs against the full Spring context and
 * a real H2 database, so the ICalService output is genuine and can be
 * validated structurally by iCal4j — the same parser real calendar clients use.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ICalRfc5545Test {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private MeetingParticipantRepository participantRepository;

    private User alice;
    private User bob;

    private static final Instant T0 = Instant.parse("2025-09-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();

        alice = userRepository.save(new User("alice_ical", "alice@example.com", "hash"));
        bob   = userRepository.save(new User("bob_ical",   "bob@example.com",   "hash"));
    }

    // ── HTTP contract ────────────────────────────────────────────────────────

    @Test
    void feed_validToken_returns200WithCalendarContentType() throws Exception {
        mockMvc.perform(get("/ical/" + alice.getIcalToken() + ".ics"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"));
    }

    @Test
    void feed_unknownToken_returns404() throws Exception {
        mockMvc.perform(get("/ical/completely-unknown-token.ics"))
                .andExpect(status().isNotFound());
    }

    @Test
    void feed_hasContentDispositionHeader() throws Exception {
        mockMvc.perform(get("/ical/" + alice.getIcalToken() + ".ics"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("meetings.ics")));
    }

    // ── RFC 5545 compliance ──────────────────────────────────────────────────

    @Test
    void feed_emptyCalendar_parsesWithoutException() throws Exception {
        String body = fetchIcal(alice.getIcalToken());

        assertDoesNotThrow(() -> new CalendarBuilder().build(new StringReader(body)),
                "iCal4j could not parse the feed — output is not valid RFC 5545");
    }

    @Test
    void feed_emptyCalendar_containsRequiredCalendarProperties() throws Exception {
        Calendar cal = parse(fetchIcal(alice.getIcalToken()));

        assertThat(cal.getProperty("VERSION").getValue()).isEqualTo("2.0");
        assertThat((Object) cal.getProperty("PRODID")).isNotNull();
    }

    @Test
    void feed_withOneMeeting_containsOneVEvent() throws Exception {
        meetingRepository.save(new Meeting(
                "Sprint Review", "Q3 demo", T0, T0.plus(1, ChronoUnit.HOURS), alice));

        List<VEvent> events = parse(fetchIcal(alice.getIcalToken()))
                .getComponents(VEvent.VEVENT);

        assertThat(events).hasSize(1);
    }

    @Test
    void feed_vevent_containsRequiredProperties() throws Exception {
        meetingRepository.save(new Meeting(
                "Team Standup", null, T0, T0.plus(30, ChronoUnit.MINUTES), alice));

        VEvent event = (VEvent) parse(fetchIcal(alice.getIcalToken()))
                .getComponents(VEvent.VEVENT).get(0);

        // RFC 5545 §3.6.1 — UID, DTSTAMP, DTSTART are REQUIRED for VEVENT.
        assertThat(event.getUid()).isNotNull();
        assertThat(event.getStartDate()).isNotNull();
        assertThat((Object) event.getProperty("DTSTAMP")).isNotNull();

        // SUMMARY carries the meeting title.
        assertThat(event.getSummary()).isNotNull();
        assertThat(event.getSummary().getValue()).isEqualTo("Team Standup");
    }

    @Test
    void feed_confirmedMeeting_hasStatusConfirmed() throws Exception {
        Meeting meeting = meetingRepository.save(new Meeting(
                "Confirmed Meeting", null, T0, T0.plus(1, ChronoUnit.HOURS), alice));
        participantRepository.save(new MeetingParticipant(meeting, bob, InviteStatus.ACCEPTED));

        VEvent event = (VEvent) parse(fetchIcal(alice.getIcalToken()))
                .getComponents(VEvent.VEVENT).get(0);

        assertThat(event.getStatus()).isNotNull();
        assertThat(event.getStatus().getValue())
                .isEqualTo(Status.VEVENT_CONFIRMED.getValue());
    }

    @Test
    void feed_tentativeMeeting_hasStatusTentative() throws Exception {
        Meeting meeting = meetingRepository.save(new Meeting(
                "Tentative Meeting", null, T0, T0.plus(1, ChronoUnit.HOURS), alice));
        participantRepository.save(new MeetingParticipant(meeting, bob, InviteStatus.PENDING));

        VEvent event = (VEvent) parse(fetchIcal(alice.getIcalToken()))
                .getComponents(VEvent.VEVENT).get(0);

        assertThat(event.getStatus()).isNotNull();
        assertThat(event.getStatus().getValue())
                .isEqualTo(Status.VEVENT_TENTATIVE.getValue());
    }

    @Test
    void feed_multipleMeetings_allAppearAsVEvents() throws Exception {
        meetingRepository.save(new Meeting("Standup", null,
                T0, T0.plus(30, ChronoUnit.MINUTES), alice));
        meetingRepository.save(new Meeting("Planning", null,
                T0.plus(2, ChronoUnit.HOURS), T0.plus(4, ChronoUnit.HOURS), alice));
        meetingRepository.save(new Meeting("Retro", null,
                T0.plus(5, ChronoUnit.HOURS), T0.plus(6, ChronoUnit.HOURS), alice));

        List<VEvent> events = parse(fetchIcal(alice.getIcalToken()))
                .getComponents(VEvent.VEVENT);

        assertThat(events).hasSize(3);
    }

    @Test
    void feed_usesCorrectCrlfLineEndings() throws Exception {
        // RFC 5545 §3.1 mandates CRLF (\r\n) as the line separator.
        meetingRepository.save(new Meeting(
                "Test Meeting", null, T0, T0.plus(1, ChronoUnit.HOURS), alice));

        String body = fetchIcal(alice.getIcalToken());

        assertThat(body).contains("BEGIN:VCALENDAR\r\n");
        assertThat(body).contains("BEGIN:VEVENT\r\n");
        assertThat(body).contains("END:VEVENT\r\n");
        assertThat(body).contains("END:VCALENDAR\r\n");
    }

    @Test
    void feed_eachUser_hasOwnIndependentFeed() throws Exception {
        meetingRepository.save(new Meeting(
                "Alice Private", null, T0, T0.plus(1, ChronoUnit.HOURS), alice));

        List<VEvent> aliceEvents = parse(fetchIcal(alice.getIcalToken()))
                .getComponents(VEvent.VEVENT);
        List<VEvent> bobEvents = parse(fetchIcal(bob.getIcalToken()))
                .getComponents(VEvent.VEVENT);

        assertThat(aliceEvents).hasSize(1);
        assertThat(bobEvents).isEmpty();
    }

    @Test
    void feed_meetingWithSpecialCharacters_parsesCleanly() throws Exception {
        // RFC 5545 §3.3.11 — commas, semicolons and backslashes must be escaped.
        meetingRepository.save(new Meeting(
                "Q3, Revenue; 50% Growth", null,
                T0, T0.plus(1, ChronoUnit.HOURS), alice));

        // iCal4j will throw ParserException if escaping is incorrect.
        assertDoesNotThrow(() -> parse(fetchIcal(alice.getIcalToken())));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String fetchIcal(String token) throws Exception {
        MvcResult result = mockMvc
                .perform(get("/ical/" + token + ".ics"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private Calendar parse(String icalText) throws ParserException, java.io.IOException {
        return new CalendarBuilder().build(new StringReader(icalText));
    }
}