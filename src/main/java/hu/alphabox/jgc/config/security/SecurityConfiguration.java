package hu.alphabox.jgc.config.security;

import hu.alphabox.jgc.config.LogTopic;
import hu.alphabox.jgc.gitea.client.GiteaClientConfigurationProperties;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.Builder;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfiguration {

  @Bean
  public HttpFirewall httpFirewall() {
    StrictHttpFirewall httpFirewall = new StrictHttpFirewall();
    httpFirewall.setAllowUrlEncodedSlash(true);
    httpFirewall.setAllowUrlEncodedPercent(true);
    return httpFirewall;
  }

  @Bean
  @Order(1)
  public SecurityFilterChain webhookSecurityFilterChain(
      HttpSecurity http,
      GitHubAppInstallationManager installationManager
  ) {
    return http.securityMatcher("/api/gitea/webhook/{installation-id}")
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .addFilterAfter(new GiteaWebhookSecurityFilter(installationManager), AuthorizationFilter.class)
        .build();
  }

  @Bean
  @Order(1)
  public SecurityFilterChain browserSecurityFilterChain(HttpSecurity http) {
    Builder requestMatcherBuilder = PathPatternRequestMatcher.withDefaults();

    return http
        .securityMatcher(new OrRequestMatcher(
            requestMatcherBuilder.matcher("/favicon.ico"),
            requestMatcherBuilder.matcher("/robots.txt"),
            requestMatcherBuilder.matcher("/assets/**"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/login/token"),
            requestMatcherBuilder.matcher("/login/oauth/access_token"),
            requestMatcherBuilder.matcher("/api/v3/app-manifests/{code}/conversions"),
            requestMatcherBuilder.matcher("/api/v3/rate_limit"),
            requestMatcherBuilder.matcher("/application-error"),
            requestMatcherBuilder.matcher("/{organization}/{repository}/tree/{branch}"),
            EndpointRequest.toAnyEndpoint()
        ))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        // TODO Should enable CSRF, but it messes up the request cache
        .csrf(AbstractHttpConfigurer::disable)
        .build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain browserProtectedSecurityFilterChain(HttpSecurity http) {
    Builder requestMatcherBuilder = PathPatternRequestMatcher.withDefaults();

    return http
        .securityMatcher(new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login/token"),
            requestMatcherBuilder.matcher("/login/oauth/authorize"),
            requestMatcherBuilder.matcher("/settings/apps/new"),
            requestMatcherBuilder.matcher("/jira-gitea-connector/installations/new"),
            requestMatcherBuilder.matcher("/installations/{installation-id}/organizations/select")
        ))
        .authorizeHttpRequests(authorize -> authorize
            .anyRequest()
            .authenticated()
        )
        .with(new TokenLoginConfigurer(), configurer -> {
          configurer.authenticationEntryPointRequestMatcher(new OrRequestMatcher(
              requestMatcherBuilder.matcher("/login/oauth/authorize"),
              requestMatcherBuilder.matcher("/settings/apps/new"),
              requestMatcherBuilder.matcher("/jira-gitea-connector/installations/new"),
              requestMatcherBuilder.matcher("/installations/{installation-id}/organizations/select")
          ));
        })
        .csrf(AbstractHttpConfigurer::disable)
        .build();

  }

  @Bean
  public AuthenticationProvider tokenAuthenticationProvider(GiteaClientConfigurationProperties properties) {
    return new TokenAuthenticationProvider(properties.accessToken());
  }

  @Bean
  @Order(3)
  public SecurityFilterChain githubAppSecurityFilterChain(
      HttpSecurity http,
      GitHubAppJwtDecoder githubAppJwtDecoder
  ) {
    return http
        .securityMatcher(
            "/api/v3/app",
            "/api/v3/app/installations/{id}",
            "/api/v3/app/installations/{id}/access_tokens"
        )
        .authorizeHttpRequests(authorize -> authorize
            .anyRequest()
            .authenticated()
        )
        .oauth2ResourceServer(customizer -> customizer
            .jwt(jwtCustomizer -> jwtCustomizer.decoder(githubAppJwtDecoder)))
        .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable)
        .build();
  }

  @Bean
  @Order(4)
  public SecurityFilterChain githubUserTokenSecurityFilterChain(
      HttpSecurity http,
      GitHubUserAccessTokenIntrospector tokenIntrospector
  ) {
    return http
        .securityMatcher(
            "/api/v3",
            "/api/v3/user",
            "/api/v3/user/installations"
        )
        .authorizeHttpRequests(authorize -> authorize
            .anyRequest()
            .authenticated()
        )
        .oauth2ResourceServer(customizer -> customizer
            .bearerTokenResolver(new GitHubUserBearerTokenResolver())
            .opaqueToken(opaqueTokenConfigurer -> opaqueTokenConfigurer.introspector(tokenIntrospector))
        )
        .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable)
        .build();
  }

  @Bean
  @Order(5)
  public SecurityFilterChain githubServerTokenSecurityFilterChain(
      HttpSecurity http,
      GitHubServerAccessTokenIntrospector tokenIntrospector
  ) {
    return http
        .authorizeHttpRequests(authorize -> authorize
            .anyRequest()
            .authenticated()
        )
        .oauth2ResourceServer(customizer -> customizer
            .authenticationEntryPoint(new LoggingAccessDeniedHandler(new BearerTokenAuthenticationEntryPoint()))
            .opaqueToken(opaqueTokenConfigurer -> opaqueTokenConfigurer.introspector(tokenIntrospector))
        )
        .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable)
        .build();
  }

  @Slf4j(topic = LogTopic.REQUEST_FAILURE)
  @RequiredArgsConstructor
  private static class LoggingAccessDeniedHandler implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint delegate;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
      log.warn("Authentication failed for following request.");
      RequestLogger.log(request, log);
      delegate.commence(request, response, authException);
    }
  }

}
