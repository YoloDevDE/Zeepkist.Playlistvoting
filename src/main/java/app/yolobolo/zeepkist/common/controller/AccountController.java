package app.yolobolo.zeepkist.common.controller;

import app.yolobolo.zeepkist.apps.playlistvoting.service.VoteService;
import app.yolobolo.zeepkist.common.model.User;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Objects;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/profile")
public class AccountController
{

    private final VoteService voteService;

    @GetMapping
    public String profile(HttpSession session, Model model, @AuthenticationPrincipal UserDetails userDetails)
    {
        String hostId = (String) session.getAttribute("hostId");
        User user = voteService.findUserById(hostId);

        List<User> managers = user != null && user.getManagerIds() != null
                ? user.getManagerIds().stream()
                  .map(voteService::findUserBySteamId)
                  .filter(Objects::nonNull)
                  .toList()
                : List.of();

        model.addAttribute("loggedIn", true);
        model.addAttribute("steamName", session.getAttribute("steamName"));
        model.addAttribute("steamId", session.getAttribute("steamId"));
        model.addAttribute("displayName", session.getAttribute("displayName"));
        model.addAttribute("token", session.getAttribute("token"));
        model.addAttribute("host", user);
        model.addAttribute("managers", managers);
        model.addAttribute("username", (userDetails != null ? userDetails.getUsername() : session.getAttribute("displayName")));

        return "common/profile";
    }

    @PostMapping("/manager/add")
    public String addManager(@RequestParam String steamId, HttpSession session)
    {
        String hostId = (String) session.getAttribute("hostId");
        User user = voteService.findUserById(hostId);
        if (user != null && !user.getManagerIds().contains(steamId))
        {
            user.getManagerIds().add(steamId);
            voteService.saveUser(user);
            log.info("Added manager steamId {} to host user {}", steamId, hostId);
        }
        return "redirect:/profile";
    }

    @PostMapping("/manager/remove")
    public String removeManager(@RequestParam String steamId, HttpSession session)
    {
        String hostId = (String) session.getAttribute("hostId");
        User user = voteService.findUserById(hostId);
        if (user != null)
        {
            user.getManagerIds().remove(steamId);
            voteService.saveUser(user);
            log.info("Removed manager steamId {} from host user {}", steamId, hostId);
        }
        return "redirect:/profile";
    }

    @PostMapping("/token/refresh")
    public String refreshToken(HttpSession session)
    {
        String hostId = (String) session.getAttribute("hostId");
        User user = voteService.findUserById(hostId);
        if (user != null)
        {
            user.setToken(java.util.UUID.randomUUID().toString().toUpperCase());
            voteService.saveUser(user);
            session.setAttribute("token", user.getToken());
            log.info("Token refreshed for host user {}", hostId);
        }
        return "redirect:/profile";
    }
}
