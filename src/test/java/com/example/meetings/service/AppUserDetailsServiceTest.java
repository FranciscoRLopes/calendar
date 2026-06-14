package com.example.meetings.service;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppUserDetailsService appUserDetailsService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("joao_maria", "joaomaria@gmail.com", "encoded_password");
    }

    @Test
    void loadUserByUsername_Success() {
        // Arrange
        when(userRepository.findByUsername("joao_maria")).thenReturn(Optional.of(user));

        // Act
        UserDetails userDetails = appUserDetailsService.loadUserByUsername("joao_maria");

        // Assert
        assertNotNull(userDetails);
        assertEquals("joao_maria", userDetails.getUsername());
        assertEquals("encoded_password", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_USER")));
        verify(userRepository, times(1)).findByUsername("joao_maria");
    }

    @Test
    void loadUserByUsername_UserNotFound_ThrowsException() {
        // Arrange
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(UsernameNotFoundException.class, () -> {
            appUserDetailsService.loadUserByUsername("unknown");
        });
        verify(userRepository, times(1)).findByUsername("unknown");
    }
}
