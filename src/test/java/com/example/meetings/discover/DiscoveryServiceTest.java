package com.example.meetings.discover;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DiscoveryServiceTest {

    @Test
    void search_MergesAndDeduplicatesAndSorts() {
        // Arrange
        EventProvider provider1 = mock(EventProvider.class);
        EventProvider provider2 = mock(EventProvider.class);

        when(provider1.isConfigured()).thenReturn(true);
        when(provider2.isConfigured()).thenReturn(true);

        DiscoveredEvent event1 = new DiscoveredEvent(
                "P1", "id1", "Title 1", "Desc", Instant.parse("2026-06-06T15:00:00Z"), null, "http://common.url", "V1"
        );
        DiscoveredEvent event2 = new DiscoveredEvent(
                "P1", "id2", "Title 2", "Desc", Instant.parse("2026-06-06T12:00:00Z"), null, "http://unique.url", "V2"
        );
        when(provider1.search("rock")).thenReturn(Arrays.asList(event1, event2));

        // Duplicate URL event (should be deduped, keeping event1 because it is parsed first)
        DiscoveredEvent event3 = new DiscoveredEvent(
                "P2", "id3", "Title 3", "Desc", Instant.parse("2026-06-06T10:00:00Z"), null, "http://common.url", "V3"
        );
        DiscoveredEvent event4 = new DiscoveredEvent(
                "P2", "id4", "Title 4", "Desc", Instant.parse("2026-06-06T18:00:00Z"), null, null, "V4"
        );
        when(provider2.search("rock")).thenReturn(Arrays.asList(event3, event4));

        DiscoveryService discoveryService = new DiscoveryService(Arrays.asList(provider1, provider2));

        // Act
        List<DiscoveredEvent> result = discoveryService.search("rock");

        // Assert
        // Result should contain: event1, event2, event4 (event3 deduped by URL).
        // Sorted by start time:
        // event2 (12:00:00Z)
        // event1 (15:00:00Z)
        // event4 (18:00:00Z)
        assertEquals(3, result.size());
        assertEquals(event2, result.get(0));
        assertEquals(event1, result.get(1));
        assertEquals(event4, result.get(2));
    }

    @Test
    void search_SkipsUnconfiguredProviders() {
        // Arrange
        EventProvider provider = mock(EventProvider.class);
        when(provider.isConfigured()).thenReturn(false);

        DiscoveryService discoveryService = new DiscoveryService(List.of(provider));

        // Act
        List<DiscoveredEvent> result = discoveryService.search("query");

        // Assert
        assertTrue(result.isEmpty());
        verify(provider, never()).search(anyString());
    }

    @Test
    void search_BlankQuery_ReturnsEmptyList() {
        DiscoveryService discoveryService = new DiscoveryService(Collections.emptyList());
        assertTrue(discoveryService.search("").isEmpty());
        assertTrue(discoveryService.search(null).isEmpty());
    }

    @Test
    void search_ProviderCrashes_SwallowsExceptionAndReturnsOtherResults() {
        // Arrange
        EventProvider healthyProvider = mock(EventProvider.class);
        EventProvider buggyProvider = mock(EventProvider.class);

        when(healthyProvider.isConfigured()).thenReturn(true);
        
        when(buggyProvider.isConfigured()).thenReturn(true);
        when(buggyProvider.name()).thenReturn("BuggyProvider");

        DiscoveredEvent event = new DiscoveredEvent(
                "P1", "id1", "Title", "Desc", Instant.parse("2026-06-06T15:00:00Z"), null, "http://url", "V"
        );
        when(healthyProvider.search("rock")).thenReturn(List.of(event));
        
        // Simulates provider throwing unexpected exception
        when(buggyProvider.search("rock")).thenThrow(new RuntimeException("API key expired or parsing error"));

        DiscoveryService discoveryService = new DiscoveryService(Arrays.asList(buggyProvider, healthyProvider));

        // Act
        List<DiscoveredEvent> result = null;
        try {
            result = discoveryService.search("rock");
        } catch (Exception ex) {
            fail("DiscoveryService should have caught and isolated the provider exception!");
        }

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(event, result.get(0));

        verify(buggyProvider, times(1)).search("rock");
        verify(healthyProvider, times(1)).search("rock");
    }
}
