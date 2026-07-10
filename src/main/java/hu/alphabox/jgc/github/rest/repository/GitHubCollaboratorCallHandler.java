package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.LinkHeaderProcessor;
import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.RepoCollaboratorPermission;
import hu.alphabox.jgc.gitea.client.model.User;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@RestController
@RequestMapping("/api/v3/repos/{owner}/{repo}/collaborators")
@RequiredArgsConstructor
class GitHubCollaboratorCallHandler {

  private final GiteaClient giteaClient;
  private final GitHubUserMapper userMapper;
  private final AllowedRepositoryService allowedRepositoryService;

  @GetMapping
  ResponseEntity<List<GitHubUserResponse>> collaborators(
      HttpServletRequest request,
      @PathVariable String owner,
      @PathVariable String repo,
      @RequestParam(value = "per_page", defaultValue = "30") Integer limit,
      @RequestParam(value = "page", defaultValue = "1") Integer page
  ) {
    if (!allowedRepositoryService.isAllowed(owner, repo)) {
      return ResponseEntity.notFound().build();
    }

    try {
      log.info("Querying collaborators for {}/{} at page {}", owner, repo, page);
      ResponseEntity<List<User>> response = giteaClient.repoListCollaborators(owner, repo, page, limit);
      if (!response.hasBody()) {
        log.info("No collaborators found for {}/{} at page {}", owner, repo, page);
        return ResponseEntity.ok(Collections.emptyList());
      }

      int totalCount = Integer.parseInt(Objects.requireNonNullElse(response.getHeaders().get("X-Total-Count"), List.of("0")).getFirst());

      URI baseURI = UriComponentsBuilder.newInstance()
          .scheme(request.getScheme())
          .host(request.getServerName())
          .port(request.getServerPort())
          .path(request.getRequestURI())
          .build()
          .toUri();

      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.LINK, LinkHeaderProcessor.create(baseURI, limit, page, totalCount).toHeader());

      log.info("Found collaborators for {}/{} at page {}", owner, repo, page);
      return ResponseEntity.ok()
          .headers(headers)
          .body(userMapper.map(response.getBody()));
    } catch (HttpClientErrorException.NotFound _) {
      log.info("No collaborators found for {}/{}", owner, repo);
      return ResponseEntity.ok(Collections.emptyList());
    }
  }

  @GetMapping("/{username}/permission")
  ResponseEntity<GitHubCollaboratorPermissionResponse> permission(
      @PathVariable String owner,
      @PathVariable String repo,
      @PathVariable String username
  ) {
    if (!allowedRepositoryService.isAllowed(owner, repo)) {
      return ResponseEntity.notFound().build();
    }

    try {
      log.info("Querying collaborator permission for {} in {}/{}", username, owner, repo);
      ResponseEntity<RepoCollaboratorPermission> response = giteaClient.repoGetCollaboratorPermission(owner, repo, username);
      RepoCollaboratorPermission permission = response.getBody();
      log.info("Found collaborator permission for {} in {}/{}", username, owner, repo);
      return ResponseEntity.ok(new GitHubCollaboratorPermissionResponse(
          mapPermission(permission.permission()),
          permission.roleName(),
          permission.user() == null ? null : userMapper.map(permission.user())
      ));
    } catch (HttpClientErrorException.NotFound _) {
      log.info("No collaborator permission found for {} in {}/{}", username, owner, repo);
      return ResponseEntity.notFound().build();
    }
  }

  private static String mapPermission(String giteaPermission) {
    if (giteaPermission == null) {
      return null;
    }
    return switch (giteaPermission.toLowerCase(Locale.ENGLISH)) {
      case "owner" -> "admin";
      case "admin" -> "admin";
      case "write" -> "write";
      case "read" -> "read";
      default -> "none";
    };
  }

}
