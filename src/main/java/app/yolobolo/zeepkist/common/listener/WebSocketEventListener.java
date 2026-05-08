package app.yolobolo.zeepkist.common.listener;

import app.yolobolo.zeepkist.common.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class WebSocketEventListener
{

    private final AtomicInteger connectionCount = new AtomicInteger(0);

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event)
    {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String user = getUserName(headerAccessor.getUser());
        String sessionId = headerAccessor.getSessionId();
        int count = connectionCount.incrementAndGet();
        log.info("WebSocket connection established: User={}, SessionId={}, Current connections={}", user, sessionId, count);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event)
    {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String user = getUserName(headerAccessor.getUser());
        String sessionId = headerAccessor.getSessionId();
        int count = connectionCount.decrementAndGet();
        log.info("WebSocket connection closed: User={}, SessionId={}, Current connections={}", user, sessionId, count);
    }

    private String getUserName(Principal principal)
    {
        if (principal instanceof UsernamePasswordAuthenticationToken auth)
        {
            if (auth.getPrincipal() instanceof User user)
            {
                return user.getDisplayName();
            }
        }
        return (principal != null) ? principal.getName() : "Anonymous";
    }
}
