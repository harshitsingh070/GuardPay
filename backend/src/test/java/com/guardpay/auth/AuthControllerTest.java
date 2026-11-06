package com.guardpay.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardpay.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private AuthService auth;
  @MockBean private CsrfTokenService csrf;

  private static User user() {
    User user = new User();
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    user.setEmail("sam@example.com");
    user.setDisplayName("Sam");
    return user;
  }

  @Test
  void loginSetsHttpOnlySessionCookieAndNeverReturnsToken() throws Exception {
    when(auth.login(any(), any(), any(), any()))
        .thenReturn(new AuthService.IssuedSession("raw-session-token", user()));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"sam@example.com\",\"password\":\"correct-horse\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Lax")))
        .andExpect(jsonPath("$.authenticated").value(true))
        .andExpect(jsonPath("$.user.displayName").value("Sam"))
        .andExpect(jsonPath("$.token").doesNotExist());
  }

  @Test
  void meIsAlwaysReachableAndReportsAnonymousWithoutContext() throws Exception {
    mockMvc
        .perform(get("/api/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(false))
        .andExpect(jsonPath("$.user").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void registerRejectsShortPasswordBeforeCallingTheService() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"sam@example.com\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest());
  }
}
