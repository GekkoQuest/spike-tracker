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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            final HttpSecurity http,
            final RateLimitingFilter rateLimitingFilter,
            @Value("${app.security.enable-hsts}") final boolean enableHsts,
            @Value("${app.security.hsts-max-age}") final long hstsMaxAge) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**", "/ws/**")
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/robots.txt").permitAll()
                        .requestMatchers("/api/health", "/api/matches", "/api/matches/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .headers(headers -> {
                    headers
                            .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                            .contentTypeOptions(contentTypeOptions -> {})
                            .addHeaderWriter(new ReferrerPolicyHeaderWriter(
                                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));

                    if (enableHsts) {
                        headers.httpStrictTransportSecurity(hstsConfig -> hstsConfig
                                .maxAgeInSeconds(hstsMaxAge)
                                .includeSubDomains(true)
                        );
                    }

                    headers.addHeaderWriter((request, response) -> {
                        response.setHeader("Content-Security-Policy",
                                "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; " +
                                        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                                        "font-src 'self' https://fonts.gstatic.com; " +
                                        "img-src 'self' data: https://www.vlr.gg https://*.vlr.gg https://owcdn.net https://*.owcdn.net; " +
                                        "connect-src 'self' https://vlrggapi.vercel.app"
                        );
                    });
                })

                .addFilterBefore(rateLimitingFilter, SecurityContextHolderAwareRequestFilter.class)

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Access Denied\",\"message\":\"Insufficient privileges\"}");
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

        private static class BucketEntry {
            private final LocalBucket bucket;
            private volatile LocalDateTime lastAccess;

            public BucketEntry(LocalBucket bucket) {
                this.bucket = bucket;
                this.lastAccess = LocalDateTime.now();
            }

            public LocalBucket getBucket() {
                this.lastAccess = LocalDateTime.now();
                return bucket;
            }

            public boolean isExpired(Duration maxAge) {
                return lastAccess.isBefore(LocalDateTime.now().minus(maxAge));
            }
        }

        private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();
        private final int requestsPerMinute;
        private final Duration maxBucketAge = Duration.ofHours(1);

        private static final int MAX_BUCKETS = 10000;

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
            final LocalBucket bucket = getBucketForClient(clientId);

            if (bucket.tryConsume(1)) {
                response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
                response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
                filterChain.doFilter(request, response);
            } else {
                log.warn("Rate limit exceeded for client: {} ({})", clientId, getAnonymizedIp(clientId));
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
                response.setHeader("Retry-After", "60");
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"message\":\"Please try again later\"}");
            }
        }

        private LocalBucket getBucketForClient(final String clientId) {
            return buckets.computeIfAbsent(clientId, this::createBucketEntry).getBucket();
        }

        private BucketEntry createBucketEntry(final String clientId) {
            if (buckets.size() >= MAX_BUCKETS) {
                log.warn("Maximum number of rate limit buckets reached ({}), forcing cleanup", MAX_BUCKETS);
                cleanupExpiredBuckets();
            }

            final LocalBucket bucket = Bucket.builder()
                    .addLimit(limit -> limit
                            .capacity(requestsPerMinute)
                            .refillIntervally(requestsPerMinute, Duration.ofMinutes(1)))
                    .build();

            return new BucketEntry(bucket);
        }

        private boolean shouldSkipRateLimiting(final String requestURI) {
            return requestURI.startsWith("/css/") ||
                    requestURI.startsWith("/js/") ||
                    requestURI.startsWith("/images/") ||
                    requestURI.equals("/favicon.ico") ||
                    requestURI.equals("/robots.txt") ||
                    requestURI.equals("/api/health");
        }

        private String getClientIdentifier(final HttpServletRequest request) {
            final String[] headerNames = {"X-Forwarded-For", "X-Real-IP", "X-Cluster-Client-IP", "CF-Connecting-IP"};

            for (String headerName : headerNames) {
                final String headerValue = request.getHeader(headerName);

                if (headerValue != null && !headerValue.isEmpty() && !"unknown".equalsIgnoreCase(headerValue)) {
                    final String[] ips = headerValue.split(",");
                    final String ip = ips[0].trim();

                    if (isValidIp(ip)) {
                        return ip;
                    }
                }
            }

            return request.getRemoteAddr();
        }

        private boolean isValidIp(String ip) {
            return ip.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$") ||
                    ip.matches("^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
        }

        private String getAnonymizedIp(final String ip) {
            if (ip.contains(".")) {
                final String[] parts = ip.split("\\.");

                if (parts.length == 4) {
                    return parts[0] + "." + parts[1] + "." + parts[2] + ".xxx";
                }
            }
            return "xxx.xxx.xxx.xxx";
        }

        @Scheduled(fixedRate = 300000)
        public void cleanupExpiredBuckets() {
            final int initialSize = buckets.size();
            buckets.entrySet().removeIf(entry -> entry.getValue().isExpired(maxBucketAge));
            final int removed = initialSize - buckets.size();

            if (removed > 0) {
                log.info("Cleaned up {} expired rate limit buckets (remaining: {})", removed, buckets.size());
            }
        }

        public int getBucketCount() {
            return buckets.size();
        }
    }
}