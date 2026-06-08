package com.example.meetings.controller;

import com.example.meetings.config.SecurityConfig;
import com.example.meetings.service.AppUserDetailsService;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private UserService userService;

    // ─── GET /login ─────────────────────────────────────────────────────────

    @Test
    void getLogin_ReturnsLoginView() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    // ─── GET /register ───────────────────────────────────────────────────────

    @Test
    void getRegister_ReturnsRegisterView() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    // ─── POST /register ──────────────────────────────────────────────────────

    @Test
    void postRegister_Success_RedirectsToLogin() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "newuser")
                        .param("email", "new@example.com")
                        .param("password", "secret"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        verify(userService).register("newuser", "new@example.com", "secret");
    }

    @Test
    void postRegister_DuplicateUsername_ReturnsRegisterViewWithError() throws Exception {
        doThrow(new IllegalArgumentException("Username already taken"))
                .when(userService).register(eq("existinguser"), any(), any());

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "existinguser")
                        .param("email", "ex@example.com")
                        .param("password", "pass"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attribute("username", "existinguser"))
                .andExpect(model().attribute("email", "ex@example.com"));
    }

    // ─── GET / ───────────────────────────────────────────────────────────────

    @Test
    void getRoot_RedirectsToCalendar() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }
}
