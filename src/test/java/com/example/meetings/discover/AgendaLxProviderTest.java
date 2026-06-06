package com.example.meetings.discover;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

public class AgendaLxProviderTest {

    private AgendaLxProvider provider;
    private MockRestServiceServer mockServer;
    private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        provider = new AgendaLxProvider(builder.build());
    }

    @Test
    void search_Success_WithTimeAndHtmlDescription() {
        // We use a future year 2030 to make sure nextOccurrence() returns a valid date.
        String jsonResponse = """
            [
              {
                "id": 12345,
                "title": { "rendered": "Teatro no Bairro" },
                "description": [
                  "<p>Esta é uma <strong>peça</strong> de teatro fantástica.</p>",
                  "<span>Não perca!</span>"
                ],
                "occurences": [
                  "2030-06-06"
                ],
                "string_times": "quinta: 21h30",
                "link": "http://agendalx.pt/event/12345",
                "venue": {
                  "v1": { "name": "Teatro São Luiz" }
                }
              }
            ]
            """;

        mockServer.expect(requestTo("/events?search=teatro&per_page=20"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> result = provider.search("teatro");

        assertEquals(1, result.size());
        DiscoveredEvent event = result.get(0);
        assertEquals("Agenda Cultural de Lisboa", event.source());
        assertEquals("12345", event.externalId());
        assertEquals("Teatro no Bairro", event.title());
        
        // Verifies HTML tags are stripped and description is joined
        assertEquals("Esta é uma  peça  de teatro fantástica.\nNão perca!", event.description());
        
        // Start time = 2030-06-06T21:30:00 in Europe/Lisbon (which is +01:00 in summer, so 20:30:00 UTC)
        Instant expectedStart = LocalDate.parse("2030-06-06")
                .atTime(LocalTime.of(21, 30))
                .atZone(LISBON)
                .toInstant();
        assertEquals(expectedStart, event.start());
        assertNull(event.end());
        assertEquals("http://agendalx.pt/event/12345", event.url());
        assertEquals("Teatro São Luiz", event.venue());

        mockServer.verify();
    }

    @Test
    void search_MalformedHtmlAndFallbackTime() {
        String jsonResponse = """
            [
              {
                "id": 12346,
                "title": { "rendered": "Concerto de Jazz" },
                "description": [
                  "Exposição << malformada > com tags"
                ],
                "occurences": [
                  "2030-06-07"
                ],
                "string_times": "Horário indisponível",
                "link": "http://agendalx.pt/event/12346",
                "venue": null
              }
            ]
            """;

        mockServer.expect(requestTo("/events?search=jazz&per_page=20"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> result = provider.search("jazz");

        assertEquals(1, result.size());
        DiscoveredEvent event = result.get(0);
        assertEquals("Concerto de Jazz", event.title());
        
        // Verifies tags
        assertEquals("Exposição   com tags", event.description());
        
        // Fallback time: 20:00 Europe/Lisbon (which is 19:00:00 UTC on 2030-06-07)
        Instant expectedStart = LocalDate.parse("2030-06-07")
                .atTime(LocalTime.of(20, 0))
                .atZone(LISBON)
                .toInstant();
        assertEquals(expectedStart, event.start());
        assertNull(event.venue());

        mockServer.verify();
    }

    @Test
    void search_HttpError_500() {
        mockServer.expect(requestTo("/events?search=teatro&per_page=20"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        List<DiscoveredEvent> result = provider.search("teatro");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void search_HttpError_429() {
        mockServer.expect(requestTo("/events?search=teatro&per_page=20"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        List<DiscoveredEvent> result = provider.search("teatro");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void search_NetworkTimeout() {
        mockServer.expect(requestTo("/events?search=teatro&per_page=20"))
                .andRespond(withException(new IOException("Connection timed out")));

        List<DiscoveredEvent> result = provider.search("teatro");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void search_SuccessWithEmptyResponse() {
        String jsonResponse = "[]";

        mockServer.expect(requestTo("/events?search=teatro&per_page=20"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> result = provider.search("teatro");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void search_SpecialCharactersQueryEncoding() {
        String jsonResponse = "[]";

        // Query: "exposição lisboa"
        // Expected URL encoding: search=exposi%C3%A7%C3%A3o%20lisboa
        mockServer.expect(requestTo("/events?search=exposi%C3%A7%C3%A3o%20lisboa&per_page=20"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> result = provider.search("exposição lisboa");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }
}
