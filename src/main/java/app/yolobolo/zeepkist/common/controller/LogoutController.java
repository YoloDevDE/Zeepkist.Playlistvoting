package app.yolobolo.zeepkist.common.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
public class LogoutController
{

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
    {
        log.info("Logout initiated for user: {}", authentication != null ? authentication.getName() : "anonymous");

        if (authentication != null)
        {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }

        return "redirect:/?logout";
    }
}
