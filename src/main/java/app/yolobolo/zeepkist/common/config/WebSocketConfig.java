package app.yolobolo.zeepkist.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer
{
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config)
    {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry)
    {
        // Handshake handler to explicitly support STOMP subprotocols
        DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler()
        {
            @Override
            public String[] getSupportedProtocols()
            {
                return new String[]{"v10.stomp", "v11.stomp", "v12.stomp"};
            }
        };

        registry.addEndpoint("/ws-dashboard")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler);

        registry.addEndpoint("/ws-dashboard")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler)
                .withSockJS();

        // Additional endpoint for direct websocket connections if the client expects it
        registry.addEndpoint("/ws-dashboard/websocket")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler);
    }
}
