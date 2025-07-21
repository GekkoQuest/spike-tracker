package quest.gekko.spiketracker.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {
    @Value("${app.websocket.allowed-origins:http://localhost:3000,http://localhost:8080,https://spike.gekko.quest}")
    private String[] allowedOrigins;

    @Value("${app.websocket.max-connections-per-ip:10}")
    private int maxConnectionsPerIp;

    private final ConcurrentHashMap<String, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();

    @Override
    public void configureMessageBroker(final MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
        config.setPreservePublishOrder(true);
    }

    @Override
    public void registerStompEndpoints(final StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS()
                .setSessionCookieNeeded(false)
                .setHeartbeatTime(25000);
    }

    @Override
    public void configureClientInboundChannel(final ChannelRegistration registration) {
        registration.interceptors(new WebSocketSecurityInterceptor());
    }

    private class WebSocketSecurityInterceptor implements ChannelInterceptor {
        @Override
        public Message<?> preSend(final Message<?> message, final MessageChannel channel) {
            final StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

            if (accessor != null) {
                final String clientIp = getClientIp(accessor);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    return handleConnect(message, clientIp);
                } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    handleDisconnect(clientIp);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    return handleSubscribe(message, accessor);
                }
            }

            return message;
        }

        private Message<?> handleConnect(final Message<?> message, final String clientIp) {
            final AtomicInteger count = connectionCounts.computeIfAbsent(clientIp, k -> new AtomicInteger(0));

            if (count.incrementAndGet() > maxConnectionsPerIp) {
                log.warn("Connection limit exceeded for IP: {} ({})", clientIp, count.get());
                count.decrementAndGet();
                return null;
            }

            log.info("WebSocket connection established from IP: {} (total: {})", clientIp, count.get());
            return message;
        }

        private void handleDisconnect(final String clientIp) {
            final AtomicInteger count = connectionCounts.get(clientIp);

            if (count != null) {
                final int remaining = count.decrementAndGet();

                if (remaining <= 0) {
                    connectionCounts.remove(clientIp);
                }

                log.info("WebSocket connection closed for IP: {} (remaining: {})", clientIp, Math.max(0, remaining));
            }
        }

        private Message<?> handleSubscribe(final Message<?> message, final StompHeaderAccessor accessor) {
            final String destination = accessor.getDestination();

            if (destination != null && !destination.startsWith("/topic/matches")) {
                log.warn("Unauthorized subscription attempt to: {}", destination);
                return null;
            }

            return message;
        }

        private String getClientIp(final StompHeaderAccessor accessor) {
            final String xForwardedFor = accessor.getFirstNativeHeader("X-Forwarded-For");

            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }

            final String xRealIp = accessor.getFirstNativeHeader("X-Real-IP");

            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }

            return accessor.getSessionAttributes() != null ?
                    accessor.getSessionAttributes().getOrDefault("REMOTE_ADDR", "unknown").toString() :
                    "unknown";
        }
    }

    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupConnectionCounts() {
        connectionCounts.entrySet().removeIf(entry -> entry.getValue().get() <= 0);

        if (connectionCounts.size() % 100 == 0 && !connectionCounts.isEmpty()) {
            log.info("Current WebSocket connections by IP: {}", connectionCounts.size());
        }
    }
}