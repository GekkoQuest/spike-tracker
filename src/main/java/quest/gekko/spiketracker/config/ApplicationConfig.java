package quest.gekko.spiketracker.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import quest.gekko.spiketracker.service.MatchTrackingService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Configuration
@EnableCaching
@EnableAsync
public class ApplicationConfig implements WebMvcConfigurer {
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${app.cache.maximum-size:10000}")
    private long cacheMaximumSize;

    @Value("${app.cache.expire-after-write:30}")
    private long cacheExpireAfterWriteMinutes;

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        boolean isProd = "prod".equals(activeProfile);

        registry.addResourceHandler("/css/**", "/js/**")
                .addResourceLocations("classpath:/static/css/", "classpath:/static/js/")
                .setCachePeriod(isProd ? 31536000 : 0) // 1 year in prod, no cache in dev
                .resourceChain(isProd);

        registry.addResourceHandler("/images/**", "/favicon.ico")
                .addResourceLocations("classpath:/static/images/", "classpath:/static/")
                .setCachePeriod(isProd ? 86400 : 300) // 1 day in prod, 5 min in dev
                .resourceChain(isProd);
    }

    @Bean
    public CacheManager cacheManager() {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(cacheMaximumSize)
                .expireAfterWrite(cacheExpireAfterWriteMinutes, TimeUnit.MINUTES)
                .recordStats()
        );

        cacheManager.setCacheNames(List.of(
                "streamLinks", "matchHistory", "teamStats",
                "healthStatus", "apiStats", "matchData"
        ));

        cacheManager.setAllowNullValues(false);

        log.info("Configured Caffeine cache manager with max size: {} and expire after write: {} minutes", cacheMaximumSize, cacheExpireAfterWriteMinutes);
        return cacheManager;
    }
    
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("SpikeTracker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.setRejectedExecutionHandler((r, executor1) -> {
            log.warn("Task rejected from thread pool: {}", r.toString());
            throw new RejectedExecutionException("Task " + r + " rejected from " + executor1.toString());
        });

        executor.initialize();
        return executor;
    }

    @ControllerAdvice
    static class GlobalExceptionHandler {
        private final MeterRegistry meterRegistry;

        public GlobalExceptionHandler(final MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @ExceptionHandler(ResourceAccessException.class)
        public ResponseEntity<Map<String, Object>> handleApiError(final ResourceAccessException ex, final WebRequest request) {
            log.error("API connection error: {}", ex.getMessage());
            meterRegistry.counter("errors", "type", "api_connection").increment();
            return createErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "VLR.gg API temporarily unavailable", request);
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, Object>> handleValidationError(final IllegalArgumentException ex, final WebRequest request) {
            log.warn("Validation error: {}", ex.getMessage());
            meterRegistry.counter("errors", "type", "validation").increment();
            return createErrorResponse(HttpStatus.BAD_REQUEST, "Invalid request parameters", request);
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<Map<String, Object>> handleConstraintViolation(final ConstraintViolationException ex, final WebRequest request) {
            log.warn("Constraint violation: {}", ex.getMessage());
            meterRegistry.counter("errors", "type", "constraint_violation").increment();
            return createErrorResponse(HttpStatus.BAD_REQUEST, "Request validation failed", request);
        }

        @ExceptionHandler(TimeoutException.class)
        public ResponseEntity<Map<String, Object>> handleTimeout(final TimeoutException ex, final WebRequest request) {
            log.error("Operation timeout: {}", ex.getMessage());
            meterRegistry.counter("errors", "type", "timeout").increment();
            return createErrorResponse(HttpStatus.REQUEST_TIMEOUT, "Operation timed out", request);
        }

        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<Map<String, Object>> handleRuntimeError(final RuntimeException ex, final WebRequest request) {
            log.error("Runtime error: {}", ex.getMessage(), ex);
            meterRegistry.counter("errors", "type", "runtime").increment();
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
        }

        @ExceptionHandler(Exception.class)
        public ModelAndView handleGenericError(final Exception ex, final WebRequest ignoredRequest) {
            log.error("Unhandled exception: {}", ex.getMessage(), ex);
            meterRegistry.counter("errors", "type", "generic").increment();

            ModelAndView modelAndView = new ModelAndView("error");
            modelAndView.addObject("error", "An unexpected error occurred");
            modelAndView.addObject("message", "Please try again later or contact support if the problem persists");
            modelAndView.addObject("timestamp", LocalDateTime.now());
            modelAndView.addObject("supportContact", "support@gekko.quest");
            return modelAndView;
        }

        private ResponseEntity<Map<String, Object>> createErrorResponse(final HttpStatus status, final String message, final WebRequest request) {
            final Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("timestamp", LocalDateTime.now());
            errorResponse.put("status", status.value());
            errorResponse.put("error", status.getReasonPhrase());
            errorResponse.put("message", message);
            errorResponse.put("path", request.getDescription(false).replace("uri=", ""));
            errorResponse.put("success", false);
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

        private final MeterRegistry meterRegistry;

        public CustomErrorController(final MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @RequestMapping("/error")
        public ResponseEntity<String> handleError(final HttpServletRequest request) {
            final String requestUri = (String) request.getAttribute("jakarta.servlet.error.request_uri");
            final Integer statusCode = (Integer) request.getAttribute("jakarta.servlet.error.status_code");
            final String errorMessage = (String) request.getAttribute("jakarta.servlet.error.message");

            if (statusCode != null) {
                meterRegistry.counter("http_errors", "status", statusCode.toString()).increment();
            }

            if (requestUri != null && isImageRequest(requestUri)) {
                log.debug("Serving default image for missing resource: {}", requestUri);
                return ResponseEntity.ok()
                        .contentType(MediaType.valueOf("image/svg+xml"))
                        .body(DEFAULT_SVG);
            }

            if (statusCode != null) {
                return switch (statusCode) {
                    case 404 -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(createJsonError(404, "Resource not found", requestUri));
                    case 403 -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(createJsonError(403, "Access denied", requestUri));
                    case 429 -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(createJsonError(429, "Rate limit exceeded", requestUri));
                    case 500 -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(createJsonError(500, "Internal server error", requestUri));
                    default -> ResponseEntity.status(statusCode)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(createJsonError(statusCode, errorMessage != null ? errorMessage : "Unknown error", requestUri));
                };
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createJsonError(500, "Internal Server Error", requestUri));
        }

        @RequestMapping({"/images/default-team.png", "/images/default-team.svg"})
        @ResponseBody
        public ResponseEntity<String> defaultTeamImage() {
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("image/svg+xml"))
                    .header("Cache-Control", "public, max-age=86400")
                    .body(DEFAULT_SVG);
        }

        private boolean isImageRequest(final String uri) {
            return uri.contains("/images/") ||
                    uri.endsWith(".png") ||
                    uri.endsWith(".jpg") ||
                    uri.endsWith(".svg") ||
                    uri.endsWith(".jpeg") ||
                    uri.endsWith(".gif") ||
                    uri.endsWith(".webp") ||
                    uri.endsWith(".ico");
        }

        private String createJsonError(final int status, final String message, final String path) {
            return String.format("""
                {
                    "timestamp": "%s",
                    "status": %d,
                    "error": "%s",
                    "message": "%s",
                    "path": "%s",
                    "success": false
                }
                """,
                    LocalDateTime.now(),
                    status,
                    HttpStatus.valueOf(status).getReasonPhrase(),
                    message != null ? message.replace("\"", "\\\"") : "Unknown error",
                    path != null ? path.replace("\"", "\\\"") : "unknown"
            );
        }
    }

    @Bean
    public HealthIndicator customHealthIndicator(final MatchTrackingService matchTrackingService) {
        return () -> {
            try {
                if (matchTrackingService.isHealthy()) {
                    return Health.up()
                            .withDetail("matches", matchTrackingService.getLiveMatchCount())
                            .withDetail("lastUpdate", matchTrackingService.getLastUpdateTime())
                            .build();
                } else {
                    return Health.down()
                            .withDetail("reason", "Match tracking service is unhealthy")
                            .build();
                }
            } catch (Exception e) {
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }
}