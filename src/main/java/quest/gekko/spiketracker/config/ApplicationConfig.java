package quest.gekko.spiketracker.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableCaching
@EnableAsync
public class ApplicationConfig implements WebMvcConfigurer {

    // ================================
    // WEBSOCKET CONFIGURATION
    // ================================

    @Configuration
    @EnableWebSocketMessageBroker
    static class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

        @Override
        public void configureMessageBroker(final MessageBrokerRegistry config) {
            config.enableSimpleBroker("/topic");
            config.setApplicationDestinationPrefixes("/app");
        }

        @Override
        public void registerStompEndpoints(final StompEndpointRegistry registry) {
            registry.addEndpoint("/ws")
                    .setAllowedOriginPatterns("*")
                    .withSockJS();
        }
    }

    // ================================
    // STATIC RESOURCE CONFIGURATION
    // ================================

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**", "/js/**")
                .addResourceLocations("classpath:/static/css/", "classpath:/static/js/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/images/**", "/favicon.ico")
                .addResourceLocations("classpath:/static/images/", "classpath:/static/")
                .setCachePeriod(86400);
    }

    // ================================
    // CACHING AND ASYNC CONFIGURATION
    // ================================

    @Bean
    public CacheManager cacheManager() {
        final ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                "streamLinks", "matchData", "teamLogos", "apiResponses"
        );
        cacheManager.setAllowNullValues(false);
        return cacheManager;
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("SpikeTracker-");
        executor.initialize();
        return executor;
    }

    // ================================
    // ERROR HANDLING
    // ================================

    @ControllerAdvice
    static class GlobalExceptionHandler {

        @ExceptionHandler(ResourceAccessException.class)
        public ResponseEntity<Map<String, Object>> handleApiError(final ResourceAccessException ex, final WebRequest request) {
            log.error("API connection error: {}", ex.getMessage());
            return createErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "Unable to connect to VLR.gg API", request);
        }

        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<Map<String, Object>> handleRuntimeError(final RuntimeException ex, final WebRequest request) {
            log.error("Runtime error: {}", ex.getMessage(), ex);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
        }

        @ExceptionHandler(Exception.class)
        public ModelAndView handleGenericError(final Exception ex, final WebRequest ignoredRequest) {
            log.error("Unhandled exception: {}", ex.getMessage(), ex);
            ModelAndView modelAndView = new ModelAndView("error");
            modelAndView.addObject("error", "An unexpected error occurred");
            modelAndView.addObject("message", ex.getMessage());
            modelAndView.addObject("timestamp", LocalDateTime.now());
            return modelAndView;
        }

        private ResponseEntity<Map<String, Object>> createErrorResponse(final HttpStatus status, final String message, final WebRequest request) {
            final Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("timestamp", LocalDateTime.now());
            errorResponse.put("status", status.value());
            errorResponse.put("error", status.getReasonPhrase());
            errorResponse.put("message", message);
            errorResponse.put("path", request.getDescription(false));
            return new ResponseEntity<>(errorResponse, status);
        }
    }

    @Controller
    static class CustomErrorController implements ErrorController {
        private static final String DEFAULT_SVG = """
            <svg width="60" height="60" viewBox="0 0 60 60" xmlns="http://www.w3.org/2000/svg">
              <circle cx="30" cy="30" r="28" fill="#2d3748" stroke="#ff6b35" stroke-width="2"/>
              <text x="30" y="37" text-anchor="middle" font-family="Arial, sans-serif" font-size="16" font-weight="bold" fill="#ffffff">?</text>
            </svg>
            """;

        @RequestMapping("/error")
        public ResponseEntity<String> handleError(final HttpServletRequest request) {
            final String requestUri = (String) request.getAttribute("jakarta.servlet.error.request_uri");
            final Integer statusCode = (Integer) request.getAttribute("jakarta.servlet.error.status_code");

            // Serve default SVG for missing images
            if (requestUri != null && isImageRequest(requestUri)) {
                log.debug("Serving default image for missing resource: {}", requestUri);
                return ResponseEntity.ok()
                        .contentType(MediaType.valueOf("image/svg+xml"))
                        .body(DEFAULT_SVG);
            }

            // Handle other 404s
            if (statusCode != null && statusCode == 404) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Resource not found");
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Internal Server Error");
        }

        @RequestMapping({"/images/default-team.png", "/images/default-team.svg"})
        @ResponseBody
        public ResponseEntity<String> defaultTeamImage() {
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("image/svg+xml"))
                    .body(DEFAULT_SVG);
        }

        private boolean isImageRequest(final String uri) {
            return uri.contains("/images/") ||
                    uri.endsWith(".png") ||
                    uri.endsWith(".jpg") ||
                    uri.endsWith(".svg") ||
                    uri.endsWith(".jpeg") ||
                    uri.endsWith(".gif");
        }
    }
}