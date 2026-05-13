package app.yolobolo.zeepkist.common.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthRestControllerTest
{

    private MockMvc mockMvc;

    @Autowired
    private AuthRestController authRestController;

    @BeforeEach
    void setup()
    {
        mockMvc = MockMvcBuilders.standaloneSetup(authRestController).build();
    }

    @Test
    void validateToken_Success() throws Exception
    {
        mockMvc.perform(get("/api/auth/validate")
                        .principal(() -> "testUser"))
                .andExpect(status().isOk());
    }

    @Test
    void validateToken_Failure() throws Exception
    {
        mockMvc.perform(get("/api/auth/validate"))
                .andExpect(status().isUnauthorized());
    }
}
