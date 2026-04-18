package app.yolobolo.zeepkist.playlistvoting;

import app.yolobolo.zeepkist.playlistvoting.model.ZkHost;
import app.yolobolo.zeepkist.playlistvoting.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Controller
@RequestMapping("/zeepkist/playlistvoting")

public class SteamAuthController {
    private static final String STEAM_OPENID_ENDPOINT = "https://steamcommunity.com/openid/login";
    private static final Pattern STEAM_ID_PATTERN =
            Pattern.compile("^https?://steamcommunity\\.com/openid/id/(\\d+)$");
    private final RestClient restClient = RestClient.create();

    @Autowired
    private AuthService authService;

    @GetMapping("/")
    public String landingPage(HttpSession session) {
        if (session.getAttribute("steamId") != null) {
            return "redirect:/zeepkist/playlistvoting/dashboard";
        }
        return "landing";
    }

    @GetMapping("/register")
    public String registerRedirect() {
        return "redirect:/zeepkist/playlistvoting/";
    }

    @GetMapping("/auth/steam")
    public RedirectView steamLogin(HttpServletRequest request) {
        String returnTo = baseUrl(request) + "/zeepkist/playlistvoting/auth/steam/callback";
        log.info("Steam login initiated, returnTo: {} - realm: {}", returnTo, baseUrl(request));
        log.info("Steam login initiated, returnTo: {}", returnTo);

        String redirectUrl = STEAM_OPENID_ENDPOINT + "?"
                + "openid.ns=" + enc("http://specs.openid.net/auth/2.0")
                + "&openid.mode=" + enc("checkid_setup")
                + "&openid.return_to=" + enc(returnTo)
                + "&openid.realm=" + enc(baseUrl(request))
                + "&openid.identity=" + enc("http://specs.openid.net/auth/2.0/identifier_select")
                + "&openid.claimed_id=" + enc("http://specs.openid.net/auth/2.0/identifier_select");

        return new RedirectView(redirectUrl);
    }

    @GetMapping("/auth/steam/callback")
    public String steamCallback(@RequestParam Map<String, String> params,
                                HttpServletRequest request,
                                HttpSession session) {
        if (!"id_res".equals(params.get("openid.mode"))) {
            log.warn("Steam login cancelled or invalid");
            return "redirect:/zeepkist/playlistvoting/?error=cancelled";
        }

        MultiValueMap<String, String> verification = new LinkedMultiValueMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("openid.")) verification.add(k, v);
        });
        verification.set("openid.mode", "check_authentication");

        String response = restClient.post()
                .uri(STEAM_OPENID_ENDPOINT)
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .body(verification)
                .retrieve()
                .body(String.class);

        if (response == null || !response.contains("is_valid:true")) {
            log.warn("Steam OpenID validation failed");
            return "redirect:/zeepkist/playlistvoting/?error=invalid";
        }

        String steamId = extractSteamId(params.get("openid.claimed_id"));
        log.info("Steam login successful - steamId: {}", steamId);

        ZkHost host = authService.findOrCreateHost(steamId);
        session.setAttribute("steamId", host.getSteamId());
        session.setAttribute("steamName", host.getSteamName());
        session.setAttribute("hostId", host.getId());
        session.setAttribute("token", host.getToken());

        return "redirect:/zeepkist/playlistvoting/dashboard";
    }

    @GetMapping("/auth/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/zeepkist/playlistvoting/";
    }

    private String extractSteamId(String claimedId) {
        if (claimedId == null) throw new IllegalStateException("No claimed_id received from Steam");
        Matcher matcher = STEAM_ID_PATTERN.matcher(claimedId);
        if (!matcher.matches()) throw new IllegalStateException("Invalid Steam claimed_id: " + claimedId);
        return matcher.group(1);
    }

    private String baseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean standardPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return standardPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
