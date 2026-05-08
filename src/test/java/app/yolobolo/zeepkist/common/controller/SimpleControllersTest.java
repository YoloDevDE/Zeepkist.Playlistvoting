package app.yolobolo.zeepkist.common.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class SimpleControllersTest
{

    private MockMvc landingMockMvc;
    private MockMvc loginMockMvc;
    private MockMvc logoutMockMvc;

    @BeforeEach
    void setup()
    {
        landingMockMvc = MockMvcBuilders.standaloneSetup(new LandingController()).build();
        loginMockMvc = MockMvcBuilders.standaloneSetup(new LoginController()).build();
        logoutMockMvc = MockMvcBuilders.standaloneSetup(new LogoutController()).build();
    }

    @Test
    void landing_Success() throws Exception
    {
        landingMockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("common/landing"));
    }

    @Test
    void login_Success() throws Exception
    {
        loginMockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("common/login"));
    }

    @Test
    void logout_Success() throws Exception
    {
        logoutMockMvc.perform(get("/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/?logout"));
    }
}
