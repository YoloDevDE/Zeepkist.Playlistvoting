package app.yolobolo.zeepkist.common.controller;

import app.yolobolo.zeepkist.common.model.User;
import app.yolobolo.zeepkist.common.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SteamAuthRestControllerTest
{

    private MockMvc mockMvc;

    @Autowired
    private SteamAuthRestController steamAuthRestController;

    @MockitoBean
    private AuthService authService;

    @BeforeEach
    void setup()
    {
        mockMvc = MockMvcBuilders.standaloneSetup(steamAuthRestController).build();
    }

    @Test
    void loginWithTicket_Success() throws Exception
    {
        String ticket = "1234567890ABCDEF";
        String steamId = "76561198000000000";
        User user = User.builder()
                .id("user123")
                .displayName("TestUser")
                .token("TEST_TOKEN")
                .build();

        when(authService.verifySteamTicket(ticket)).thenReturn(steamId);
        when(authService.findOrCreateUser(steamId)).thenReturn(user);

        mockMvc.perform(post("/api/auth/steam/ticket")
                        .content(ticket)
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user123"))
                .andExpect(jsonPath("$.token").value("TEST_TOKEN"))
                .andExpect(jsonPath("$.displayName").value("TestUser"))
                .andExpect(jsonPath("$.steamId").value(steamId));
    }

    @Test
    void loginWithTicket_Failure() throws Exception
    {
        when(authService.verifySteamTicket(anyString())).thenReturn(null);

        mockMvc.perform(post("/api/auth/steam/ticket")
                        .content("INVALID_TICKET")
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isUnauthorized());
    }
}
