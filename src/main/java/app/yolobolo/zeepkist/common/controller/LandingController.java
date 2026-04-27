package app.yolobolo.zeepkist.common.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingController
{

    @GetMapping({"/", "/index"})
    public String index(Model model, HttpSession session)
    {
        addCommonAttributes(model, session);
        return "common/landing";
    }

    private void addCommonAttributes(Model model, HttpSession session)
    {
        String hostId = (String) session.getAttribute("hostId");
        model.addAttribute("loggedIn", hostId != null);
        if (hostId != null)
        {
            model.addAttribute("displayName", session.getAttribute("displayName"));
            model.addAttribute("steamName", session.getAttribute("steamName"));
            model.addAttribute("steamId", session.getAttribute("steamId"));
            model.addAttribute("hostId", hostId);
        }
    }

}
