package app.yolobolo.zeepkist.common.controller;

import app.yolobolo.zeepkist.common.model.SteamUserPrincipal;
import app.yolobolo.zeepkist.common.model.User;
import app.yolobolo.zeepkist.common.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Controller
public class SteamAuthController
{
    private static final String STEAM_ID_REGEX = "^https?://steamcommunity\\.com/openid/id/(\\d+)$";
    private static final Pattern STEAM_ID_PATTERN = Pattern.compile(STEAM_ID_REGEX);

    private static final String CALLBACK_PATH = "/auth/steam/callback";
    private static final String DASHBOARD_REDIRECT = "redirect:/playlistvoting/dashboard";
    private static final String ERROR_INVALID = "redirect:/?error=invalid";
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
    @Autowired
    private AuthService authService;

    @GetMapping("/auth/steam")
    public RedirectView steamLogin(HttpServletRequest request)
    {
        String baseUrl = constructBaseUrl(request);
        String returnTo = baseUrl + CALLBACK_PATH;

        log.info("Steam login initiated, returnTo: {}", returnTo);
        String redirectUrl = authService.getSteamLoginUrl(returnTo, baseUrl);

        return new RedirectView(redirectUrl);
    }

    @GetMapping("/auth/steam/callback")
    public String steamCallback(@RequestParam Map<String, String> params,
                                HttpServletRequest request,
                                HttpServletResponse response)
    {
        String claimedId = authService.verifySteamLogin(params);

        if (claimedId == null)
        {
            return ERROR_INVALID;
        }

        String steamId = extractSteamId(claimedId);
        log.info("Steam login successful - steamId: {}", steamId);

        User user = authService.handleSteamLogin(steamId);
        authenticateUser(user, request, response);

        return DASHBOARD_REDIRECT;
    }

    private void authenticateUser(User user, HttpServletRequest request, HttpServletResponse response)
    {
        SteamUserPrincipal principal = SteamUserPrincipal.fromUser(user);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private String extractSteamId(String claimedId)
    {
        if (claimedId == null)
        {
            throw new IllegalStateException("No claimed_id received from Steam");
        }
        Matcher matcher = STEAM_ID_PATTERN.matcher(claimedId);
        if (!matcher.matches())
        {
            throw new IllegalStateException("Invalid Steam claimed_id: " + claimedId);
        }
        return matcher.group(1);
    }

    private String constructBaseUrl(HttpServletRequest request)
    {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean standardPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return standardPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }
}
