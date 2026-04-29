package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.ContentsResponse;
import hu.alphabox.jgc.gitea.client.model.Repository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@RestController
@RequestMapping("/api/v3/repos/{owner}/{repo}")
@RequiredArgsConstructor
class GitHubRepositoryCallHandler {

  private final GiteaClient giteaClient;
  private final GitHubRepositoryMapper mapper;
  private final AllowedRepositoryService allowedRepositoryService;

  @GetMapping
  ResponseEntity<GitHubRepositoryResponse> handle(@PathVariable String owner, @PathVariable String repo) {
    if (!allowedRepositoryService.isAllowed(owner, repo)) {
      return ResponseEntity.notFound().build();
    }

    try {
      log.info("Querying for repository: {}/{}", owner, repo);
      ResponseEntity<Repository> repository = giteaClient.repoGet(owner, repo);
      return ResponseEntity.ok(mapper.repository(repository.getBody()));
    } catch (HttpClientErrorException.NotFound _) {
      log.info("Repository not found: {}/{}", owner, repo);
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping("/contents/**")
  ResponseEntity<GitHubContentTreeResponse> handleContents(
      HttpServletRequest request,
      @PathVariable String owner,
      @PathVariable String repo
  ) {
    if (!allowedRepositoryService.isAllowed(owner, repo)) {
      return ResponseEntity.notFound().build();
    }

    String path = request.getRequestURI().split("/contents/", 2)[1];
    try {
      log.info("Querying for repository contents {} in: {}/{}", path, owner, repo);
      ResponseEntity<ContentsResponse> response = giteaClient.repoGetContents(owner, repo, path, null);
      log.info("Found repository contents {} in: {}/{}", path, owner, repo);
      return ResponseEntity.ok(mapper.content(response.getBody()));
    } catch (HttpClientErrorException.NotFound _) {
      log.info("No repository contents {} found in: {}/{}", path, owner, repo);
      return ResponseEntity.notFound().build();
    }
  }

}
