package app.yolobolo.zeepkist.common.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingController
{

    @GetMapping({"/", "/playlistvoting", "/playlistvoting/"})
    public String index(Model model, HttpSession session)
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean loggedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);

        model.addAttribute("loggedIn", loggedIn);
        if (loggedIn)
        {
            String displayName = (String) session.getAttribute("displayName");
            if (displayName == null)
            {
                displayName = auth.getName();
            }
            model.addAttribute("username", auth.getName());
            model.addAttribute("displayName", displayName);
        }
        return "common/landing";
    }

}
