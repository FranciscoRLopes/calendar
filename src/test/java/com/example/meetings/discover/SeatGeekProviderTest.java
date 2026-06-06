package com.example.meetings.discover;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

public class SeatGeekProviderTest {

    private SeatGeekProvider provider;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        provider = new SeatGeekProvider("client-id-abc", builder.build());
    }

    @Test
    void search_Success() {
        String jsonResponse = """
            {
              "events": [
                {
                  "id": 999,
                  "title": "FC Porto vs Benfica",
                  "description": "Football match",
                  "datetime_utc": "2026-06-06T20:45:00",
                  "url": "http://sg.com/event999",
                  "venue": {
                    "name": "Estádio do Dragão"
                  }
                }
              ]
            }
            """;

        mockServer.expect(requestTo("/events?q=porto&per_page=20&client_id=client-id-abc"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> result = provider.search("porto");

        assertEquals(1, result.size());
        DiscoveredEvent event = result.get(0);
        assertEquals("SeatGeek", event.source());
        assertEquals("999", event.externalId());
        assertEquals("FC Porto vs Benfica", event.title());
        assertEquals("Football match", event.description());
        assertEquals(Instant.parse("2026-06-06T20:45:00Z"), event.start());
        assertNull(event.end());
        assertEquals("http://sg.com/event999", event.url());
        assertEquals("Estádio do Dragão", event.venue());

        mockServer.verify();
    }

    @Test
    void search_HttpError_500() {
        mockServer.expect(requestTo("/events?q=porto&per_page=20&client_id=client-id-abc"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        List<DiscoveredEvent> result = provider.search("porto");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void search_HttpError_429() {
        mockServer.expect(requestTo("/events?q=porto&per_page=20&client_id=client-id-abc"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        List<DiscoveredEvent> result = provider.search("porto");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void search_NetworkTimeout() {
        mockServer.expect(requestTo("/events?q=porto&per_page=20&client_id=client-id-abc"))
                .andRespond(withException(new IOException("Connection timed out")));

        List<DiscoveredEvent> result = provider.search("porto");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void search_SuccessWithNullEventsList() {
        String jsonResponse = "{\"events\": null}";

        mockServer.expect(requestTo("/events?q=porto&per_page=20&client_id=client-id-abc"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> result = provider.search("porto");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void search_SuccessWithEmptyEventsList() {
        String jsonResponse = "{\"events\": []}";

        mockServer.expect(requestTo("/events?q=porto&per_page=20&client_id=client-id-abc"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> result = provider.search("porto");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void search_SpecialCharactersQueryEncoding() {
        String jsonResponse = "{\"events\": []}";

        // Query: "futebol dragão"
        // Expected URL encoding: q=futebol%20drag%C3%A3o
        mockServer.expect(requestTo("/events?q=futebol%20drag%C3%A3o&per_page=20&client_id=client-id-abc"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> result = provider.search("futebol dragão");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }
}
