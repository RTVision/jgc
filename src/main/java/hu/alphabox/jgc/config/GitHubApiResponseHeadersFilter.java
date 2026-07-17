package hu.alphabox.jgc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * GitHub Enterprise Server stamps every API response with version, media-type and rate-limit
 * headers. Integrations may gate on them (e.g. a minimum supported GHES version), so mirror them
 * on everything under /api/v3.
 */
@Component
public class GitHubApiResponseHeadersFilter extends OncePerRequestFilter {

  // keep in sync with the version advertised in webhook deliveries
  private static final String GITHUB_ENTERPRISE_VERSION = "3.17.6";

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v3");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    response.setHeader("X-GitHub-Enterprise-Version", GITHUB_ENTERPRISE_VERSION);
    response.setHeader("X-GitHub-Media-Type", "github.v3; format=json");
    response.setHeader("X-GitHub-Request-Id", UUID.randomUUID().toString().toUpperCase());
    response.setHeader("X-RateLimit-Limit", "5000");
    response.setHeader("X-RateLimit-Remaining", "4999");
    response.setHeader("X-RateLimit-Used", "1");
    response.setHeader("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(3600).getEpochSecond()));
    response.setHeader("X-RateLimit-Resource", "core");
    filterChain.doFilter(request, response);
  }
}
