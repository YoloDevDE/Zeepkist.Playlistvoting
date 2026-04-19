package app.yolobolo.zeepkist.common.config;

import app.yolobolo.zeepkist.common.repository.UserRepo;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserRepo userRepo;

    @Override
    public void onAuthenticationSuccess(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, Authentication authentication) throws ServletException, IOException {
        String email = authentication.getName();
        userRepo.findByEmail(email).ifPresent(user -> {
            HttpSession session = request.getSession();
            session.setAttribute("userId", user.getId());
            session.setAttribute("displayName", user.getDisplayName());
            session.setAttribute("hostId", user.getId());
            session.setAttribute("token", user.getToken());

            String steamId = user.getSteamId();
            if (steamId != null) {
                session.setAttribute("steamId", steamId);
                session.setAttribute("steamName", user.getDisplayName());
            }
        });
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
