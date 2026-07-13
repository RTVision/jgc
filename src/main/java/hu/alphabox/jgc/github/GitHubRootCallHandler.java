package hu.alphabox.jgc.github;

import hu.alphabox.jgc.ApplicationProperties;
import hu.alphabox.jgc.github.GitHubRootCallHandler.RateLimitResponse.Resources;
import hu.alphabox.jgc.github.GitHubRootCallHandler.RateLimitResponse.Resources.Resource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@RestController
@RequestMapping("/api/v3")
@RequiredArgsConstructor
@EnableConfigurationProperties(ApplicationProperties.class)
class GitHubRootCallHandler {

  @GetMapping(produces = "application/vnd.github.v3+json")
  ResponseEntity<String> handleMetaRoot() {
    return ResponseEntity.ok().build();
  }

  record RateLimitResponse(
      Resources resources,
      // GitHub also returns the core resource as a top-level "rate" object; some clients only read this
      Resource rate
  ) {

    record Resources(
        Resource graphql,
        Resource core
    ) {

      record Resource(
          Integer remaining,
          Integer limit,
          long reset
      ) {

      }
    }
  }

  @JsonNaming(SnakeCaseStrategy.class)
  record MetaResponse(
      boolean verifiablePasswordAuthentication
  ) {

  }

  @GetMapping("/meta")
  ResponseEntity<MetaResponse> handleMeta() {
    return ResponseEntity.ok(new MetaResponse(false));
  }

  @GetMapping("/rate_limit")
  ResponseEntity<RateLimitResponse> handleRateLimit() {
    //Gitea currently does not have rate limiting.
    //The header is required so that Jira “believes” we are a GitHub app.
    Resource resource = new Resource(50500, 50500, Instant.now().plus(1, ChronoUnit.MINUTES).getEpochSecond());
    return ResponseEntity.ok()
        .header("x-github", "fake")
        .body(new RateLimitResponse(new Resources(resource, resource), resource));
  }
}
