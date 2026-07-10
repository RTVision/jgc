package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/repos/{owner}/{repo}/dependabot/alerts")
@RequiredArgsConstructor
class GitHubDependabotAlertCallHandler {

  private final AllowedRepositoryService allowedRepositoryService;

  record ErrorResponse(String message) {

  }

  @GetMapping
  ResponseEntity<ErrorResponse> alerts(@PathVariable String owner, @PathVariable String repo) {
    if (!allowedRepositoryService.isAllowed(owner, repo)) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.status(404).body(new ErrorResponse("Dependabot alerts are disabled for this repository"));
  }

}
