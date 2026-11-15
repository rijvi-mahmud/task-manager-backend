package com.example.backend.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.example.backend.identity.security.AuthRateLimiter;
import com.example.backend.identity.security.AuthRateLimitFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Session-cookie authentication for a browser SPA.
 *
 * <ul>
 *   <li><b>Who:</b> an HttpOnly {@code SESSION} cookie (Spring Session, stored in
 *       PostgreSQL). JavaScript cannot read it, so XSS cannot steal it, and signing
 *       out ends it on the server immediately.</li>
 *   <li><b>CSRF:</b> because browsers attach cookies automatically, every unsafe
 *       request must also echo the {@code XSRF-TOKEN} cookie in the
 *       {@code X-XSRF-TOKEN} header (double-submit cookie). Other sites cannot read
 *       the cookie, so they cannot forge the header. Applies to sign-in too
 *       (prevents login CSRF).</li>
 *   <li><b>What:</b> everything under {@code /api} needs a session except the
 *       auth endpoints. Per-workspace permissions are checked by
 *       {@code WorkspaceAccess} in the services.</li>
 *   <li><b>Errors:</b> 401/403 are rendered by {@code GlobalExceptionHandler} as
 *       RFC 9457 problems, like every other error.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class SecurityConfig {

    private static final String AUTH = "/api/*/auth/";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
                                    SecurityContextRepository securityContextRepository, AuthRateLimiter authRateLimiter,
                                    @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        http
                .addFilterBefore(new AuthRateLimitFilter(authRateLimiter, exceptionResolver), CsrfFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, AUTH + "csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, AUTH + "register", AUTH + "login",
                                AUTH + "password-reset", AUTH + "password-reset/confirm").permitAll()
                        .requestMatchers("/error", "/actuator/health", "/actuator/health/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .cors(Customizer.withDefaults())
                // JSON endpoints in AuthController replace the HTML-oriented defaults.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                exceptionResolver.resolveException(request, response, null, ex))
                        .accessDeniedHandler((request, response, ex) ->
                                exceptionResolver.resolveException(request, response, null, ex)));
        return http.build();
    }

    /** Readable by the SPA (not HttpOnly) by design: that is how it echoes the token in the header. */
    @Bean
    CsrfTokenRepository csrfTokenRepository(@Value("${server.servlet.session.cookie.secure}") boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secure));
        return repository;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    /** bcrypt today; the {id} prefix in stored hashes lets the algorithm change later without a reset. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
