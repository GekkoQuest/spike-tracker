package quest.gekko.spiketracker.config;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.local.LocalBucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Value("${app.security.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String[] allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(final HttpSecurity http, final RateLimitingFilter rateLimitingFilter) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**", "/ws/**")
                )

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                )

                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/health", "/api/matches", "/api/matches/history").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN") // Protect other actuator endpoints
                        .anyRequest().permitAll()
                )

                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentTypeOptions(ignored -> {})
                        .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                                .maxAgeInSeconds(31536000)
                                .includeSubDomains(true)
                        )
                        .addHeaderWriter(new ReferrerPolicyHeaderWriter(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter((request, response) -> {
                            response.setHeader("X-Content-Type-Options", "nosniff");
                            response.setHeader("X-XSS-Protection", "1; mode=block");
                            response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
                        })
                )

                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((httpIgnored, response, authIgnored) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.getWriter().write("Unauthorized");
                        })
                        .accessDeniedHandler((request, response, ignored) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.getWriter().write("Access Denied");
                        })
                );

        return http.build();
    }

    @Bean
    public RateLimitingFilter rateLimitingFilter(@Value("${app.security.rate-limit.requests-per-minute:60}") final int requestsPerMinute) {
        return new RateLimitingFilter(requestsPerMinute);
    }

    @Component
    public static class RateLimitingFilter extends OncePerRequestFilter {

        private final ConcurrentHashMap<String, LocalBucket> buckets = new ConcurrentHashMap<>();
        private final int requestsPerMinute;

        public RateLimitingFilter(@Value("${app.security.rate-limit.requests-per-minute:60}") final int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }

        @Override
        protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain) throws IOException, ServletException {
            final String requestURI = request.getRequestURI();

            if (shouldSkipRateLimiting(requestURI)) {
                filterChain.doFilter(request, response);
                return;
            }

            final String clientId = getClientIdentifier(request);
            final LocalBucket bucket = buckets.computeIfAbsent(clientId, this::createBucket);

            if (bucket.tryConsume(1)) {
                response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
                response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
                filterChain.doFilter(request, response);
            } else {
                log.warn("Rate limit exceeded for client: {}", clientId);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
                response.setHeader("Retry-After", "60");
                response.getWriter().write("Rate limit exceeded. Please try again later.");
            }
        }

        private boolean shouldSkipRateLimiting(final String requestURI) {
            return requestURI.startsWith("/css/") ||
                    requestURI.startsWith("/js/") ||
                    requestURI.startsWith("/images/") ||
                    requestURI.equals("/favicon.ico") ||
                    requestURI.equals("/api/health");
        }

        private String getClientIdentifier(final HttpServletRequest request) {
            final String xForwardedFor = request.getHeader("X-Forwarded-For");

            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }

            return request.getRemoteAddr();
        }

        private LocalBucket createBucket(final String clientId) {
            return Bucket.builder()
                    .addLimit(limit -> limit
                            .capacity(requestsPerMinute)
                            .refillIntervally(requestsPerMinute, Duration.ofMinutes(1)))
                    .build();
        }

        @Scheduled(fixedRate = 300000) // 5 minutes
        public void cleanupBuckets() {
            if (buckets.size() > 1000) {
                log.info("Cleaning up {} rate limit buckets", buckets.size());
                buckets.clear();
            }
        }
    }
}