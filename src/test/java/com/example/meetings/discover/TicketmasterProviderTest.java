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

public class TicketmasterProviderTest {

    private TicketmasterProvider provider;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        provider = new TicketmasterProvider("api-key-123", "PT", builder.build());
    }

    @Test
    void search_Success() {
        String jsonResponse = """
            {
              "_embedded": {
                "events": [
                  {
                    "id": "tm-1",
                    "name": "Rock Festival",
                    "url": "http://tm.com/rock-fest",
                    "info": "Featuring various bands",
                    "dates": {
                      "start": {
                        "dateTime": "2026-06-06T18:00:00Z"
                      }
                    },
                    "_embedded": {
                      "venues": [
                        { "name": "Lisbon Arena" }
                      ]
                    }
                  }
                ]
              }
            }
            """;

        mockServer.expect(requestTo("/events.json?keyword=rock&size=20&apikey=api-key-123&countryCode=PT"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> result = provider.search("rock");

        assertEquals(1, result.size());
        DiscoveredEvent event = result.get(0);
        assertEquals("Ticketmaster", event.source());
        assertEquals("tm-1", event.externalId());
        assertEquals("Rock Festival", event.title());
        assertEquals("Featuring various bands", event.description());
        assertEquals(Instant.parse("2026-06-06T18:00:00Z"), event.start());
        assertNull(event.end());
        assertEquals("http://tm.com/rock-fest", event.url());
        assertEquals("Lisbon Arena", event.venue());

        mockServer.verify();
    }

    @Test
    void search_HttpError_500() {
        mockServer.expect(requestTo("/events.json?keyword=rock&size=20&apikey=api-key-123&countryCode=PT"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        List<DiscoveredEvent> result = provider.search("rock");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void search_HttpError_429() {
        mockServer.expect(requestTo("/events.json?keyword=rock&size=20&apikey=api-key-123&countryCode=PT"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        List<DiscoveredEvent> result = provider.search("rock");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void search_NetworkTimeout() {
        mockServer.expect(requestTo("/events.json?keyword=rock&size=20&apikey=api-key-123&countryCode=PT"))
                .andRespond(withException(new IOException("Connection timed out")));

        List<DiscoveredEvent> result = provider.search("rock");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void search_SuccessWithEmptyEmbedded() {
        String jsonResponse = "{ }";

        mockServer.expect(requestTo("/events.json?keyword=rock&size=20&apikey=api-key-123&countryCode=PT"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> result = provider.search("rock");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void search_SuccessWithNullEvents() {
        String jsonResponse = "{\"_embedded\": {\"events\": null}}";

        mockServer.expect(requestTo("/events.json?keyword=rock&size=20&apikey=api-key-123&countryCode=PT"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> result = provider.search("rock");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void search_SpecialCharactersQueryEncoding() {
        String jsonResponse = "{\"_embedded\": {\"events\": []}}";

        // Query: "música eletrónica"
        // Expected URL encoding: keyword=m%C3%BAsica%20eletr%C3%B3nica
        mockServer.expect(requestTo("/events.json?keyword=m%C3%BAsica%20eletr%C3%B3nica&size=20&apikey=api-key-123&countryCode=PT"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> result = provider.search("música eletrónica");

        assertTrue(result.isEmpty());
        mockServer.verify();
    }
}
