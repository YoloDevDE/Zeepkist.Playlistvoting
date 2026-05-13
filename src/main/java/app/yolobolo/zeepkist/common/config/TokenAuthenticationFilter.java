package app.yolobolo.zeepkist.common.config;

import app.yolobolo.zeepkist.common.model.SteamUserPrincipal;
import app.yolobolo.zeepkist.common.model.User;
import app.yolobolo.zeepkist.common.repository.UserRepository;
import app.yolobolo.zeepkist.common.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter
{

    private final UserRepository userRepo;
    private final AuthService authService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException
    {

        String token = extractToken(request);

        if (token == null)
        {
            if (request.getRequestURI().contains("/ws-dashboard"))
            {
                log.debug("WebSocket handshake request for /ws-dashboard WITHOUT token. Auth header: {}, Method: {}",
                        request.getHeader("Authorization"), request.getMethod());
            }
            if (request.getRequestURI().endsWith("/validate"))
            {
                log.warn("No token found in request for /validate. URI: {}", request.getRequestURI());
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null)
        {
            filterChain.doFilter(request, response);
            return;
        }

        if (request.getRequestURI().contains("/ws-dashboard"))
        {
            log.debug("WebSocket handshake request for /ws-dashboard with token. Method: {}", request.getMethod());
        }

        log.debug("Found token in request, attempting authentication. Request URI: {}", request.getRequestURI());

        User user = userRepo.findByToken(token).orElse(null);

        if (user == null && isPotentialSteamTicket(token))
        {
            log.info("Token looks like a Steam ticket, attempting verification...");
            String steamId = authService.verifySteamTicket(token);
            if (steamId != null)
            {
                log.info("Steam ticket verified. SteamID: {}", steamId);
                user = authService.findOrCreateUser(steamId);
            }
        }

        if (user == null)
        {
            if (isPotentialSteamTicket(token))
            {
                log.warn("Invalid Steam ticket provided (length: {}): {}", token.length(), token);
            }
            else
            {
                log.warn("Invalid app token provided (length: {}): {}", token.length(), token);
            }
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("User authenticated: {} (SteamID: {})", user.getDisplayName(), user.getSteamId());
        SteamUserPrincipal principal = SteamUserPrincipal.fromUser(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request)
    {
        // 1. Check Authorization Header
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer "))
        {
            return bearerToken.substring(7);
        }

        // 2. Check Query Parameter (for Chatbots as requested)
        return request.getParameter("token");
    }

    private boolean isPotentialSteamTicket(String token)
    {
        // Steam tickets are long hex strings (usually > 200 chars)
        return token != null && token.length() > 100 && token.matches("[0-9A-Fa-f]+");
    }
}
