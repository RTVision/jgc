package hu.alphabox.jgc.config.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

class TokenLoginConfigurer extends AbstractHttpConfigurer<TokenLoginConfigurer, HttpSecurity> {

  private final TokenAuthenticationFilter filter;

  private RequestMatcher authenticationEntryPointRequestMatcher = new AndRequestMatcher(
      new NegatedRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/login/token")),
      PathPatternRequestMatcher.withDefaults().matcher("/**")
  );

  TokenLoginConfigurer() {
    filter = new TokenAuthenticationFilter();
  }

  TokenLoginConfigurer authenticationEntryPointRequestMatcher(RequestMatcher authenticationEntryPointRequestMatcher) {
    this.authenticationEntryPointRequestMatcher = authenticationEntryPointRequestMatcher;
    registerAuthenticationEntryPoint(getBuilder());
    return this;
  }

  private AuthenticationEntryPoint buildAuthenticationEntryPoint() {
    return new LoginUrlAuthenticationEntryPoint("/login/token");
  }

  private void registerAuthenticationEntryPoint(HttpSecurity http) {
    if (authenticationEntryPointRequestMatcher == null) {
      return;
    }

    ExceptionHandlingConfigurer<HttpSecurity> exceptionHandling = http.getConfigurer(ExceptionHandlingConfigurer.class);
    if (exceptionHandling == null) {
      return;
    }

    exceptionHandling.defaultAuthenticationEntryPointFor(postProcess(buildAuthenticationEntryPoint()), authenticationEntryPointRequestMatcher);
  }

  @Override
  public void init(HttpSecurity http) {
    super.init(http);

    filter.setAuthenticationFailureHandler(new SimpleUrlAuthenticationFailureHandler("/login/token?error"));
    registerAuthenticationEntryPoint(http);
  }

  @Override
  public void configure(HttpSecurity http) {
    super.configure(http);

    filter.setAuthenticationManager(http.getSharedObject(AuthenticationManager.class));
    filter.setSecurityContextHolderStrategy(getSecurityContextHolderStrategy());

    SessionAuthenticationStrategy sessionAuthenticationStrategy = http.getSharedObject(SessionAuthenticationStrategy.class);
    if (sessionAuthenticationStrategy != null) {
      filter.setSessionAuthenticationStrategy(sessionAuthenticationStrategy);
    }

    SecurityContextRepository securityContextRepository = http.getSharedObject(SecurityContextRepository.class);
    if (securityContextRepository != null) {
      filter.setSecurityContextRepository(securityContextRepository);
    }

    RequestCache requestCache = http.getSharedObject(RequestCache.class);
    if (requestCache != null) {
      DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
      redirectStrategy.setStatusCode(HttpStatus.TEMPORARY_REDIRECT);

      SavedRequestAwareAuthenticationSuccessHandler successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
      successHandler.setRedirectStrategy(redirectStrategy);
      successHandler.setRequestCache(requestCache);
      filter.setAuthenticationSuccessHandler(successHandler);
    }

    http.addFilterAfter(filter, AbstractPreAuthenticatedProcessingFilter.class);
  }
}
