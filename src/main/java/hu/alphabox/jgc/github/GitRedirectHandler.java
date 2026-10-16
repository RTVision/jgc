package hu.alphabox.jgc.github;

import hu.alphabox.jgc.gitea.GiteaConfigurationProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

// Integrations build browser links from the GitHub base URL, which is this connector; send people on to Gitea
@RestController
@RequiredArgsConstructor
class GitRedirectHandler {

  private final GiteaConfigurationProperties giteaProperties;

  @GetMapping({
      "/{organization}/{repository}/tree/{branch}",
      "/{organization}/{repository}/commit/{sha}",
      "/{organization}/{repository}/compare/**"
  })
  ResponseEntity<Void> samePathRedirect(HttpServletRequest request) {
    return redirect(decodedPath(request));
  }

  // GitHub's blob/<ref>/<path>; Gitea's src/<ref>/<path> resolves a bare branch, tag, or commit ref
  @GetMapping("/{organization}/{repository}/blob/**")
  ResponseEntity<Void> blobRedirect(
      HttpServletRequest request,
      @PathVariable String organization,
      @PathVariable String repository
  ) {
    String prefix = "/%s/%s/blob/".formatted(organization, repository);
    return redirect("/%s/%s/src/%s".formatted(organization, repository, decodedPath(request).substring(prefix.length())));
  }

  @GetMapping("/{organization}/{repository}/pull/{number}")
  ResponseEntity<Void> pullRedirect(
      @PathVariable String organization,
      @PathVariable String repository,
      @PathVariable long number
  ) {
    return redirect("/%s/%s/pulls/%d".formatted(organization, repository, number));
  }

  private static String decodedPath(HttpServletRequest request) {
    return URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);
  }

  private ResponseEntity<Void> redirect(String path) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(
            UriComponentsBuilder.fromUriString(giteaProperties.baseUrl())
                .path(path)
                .build()
                .toUri()
        )
        .build();
  }

}
