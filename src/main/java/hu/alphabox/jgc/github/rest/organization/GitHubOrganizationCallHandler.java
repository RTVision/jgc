package hu.alphabox.jgc.github.rest.organization;

import hu.alphabox.jgc.LinkHeaderProcessor;
import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.GiteaConfigurationProperties;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.Organization;
import hu.alphabox.jgc.gitea.client.model.Repository;
import hu.alphabox.jgc.gitea.client.model.User;
import hu.alphabox.jgc.github.rest.repository.GitHubRepositoryMapper;
import hu.alphabox.jgc.github.rest.repository.GitHubRepositoryResponse;
import hu.alphabox.jgc.github.rest.repository.GitHubUserMapper;
import hu.alphabox.jgc.github.rest.repository.GitHubUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
@RequestMapping("/api/v3/orgs/{org}")
@RequiredArgsConstructor
class GitHubOrganizationCallHandler {

  private final GiteaClient giteaClient;
  private final GiteaConfigurationProperties giteaConfigurationProperties;
  private final AllowedRepositoryService allowedRepositoryService;
  private final GitHubRepositoryMapper repositoryMapper;
  private final GitHubUserMapper userMapper;

  @GetMapping
  ResponseEntity<GitHubOrganizationResponse> organization(@PathVariable String org) {
    try {
      log.info("Querying for organization: {}", org);
      ResponseEntity<Organization> response = giteaClient.orgGet(org);
      log.info("Organization found: {}", org);
      return ResponseEntity.ok(map(response.getBody()));
    } catch (HttpClientErrorException.NotFound _) {
      log.info("Organization not found: {}", org);
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping("/repos")
  ResponseEntity<List<GitHubRepositoryResponse>> repos(
      HttpServletRequest request,
      @PathVariable String org,
      @RequestParam(value = "per_page", defaultValue = "30") Integer limit,
      @RequestParam(value = "page", defaultValue = "1") Integer page
  ) {
    try {
      log.info("Querying repositories for organization {} at page {}", org, page);
      ResponseEntity<List<Repository>> response = giteaClient.orgListRepos(org, page, limit);
      if (!response.hasBody()) {
        log.info("No repositories found for organization {} at page {}", org, page);
        return ResponseEntity.ok(Collections.emptyList());
      }

      List<GitHubRepositoryResponse> repositories = response.getBody().stream()
          .filter(repository -> repository.fullName() != null && allowedRepositoryService.isAllowed(repository.fullName()))
          .map(repositoryMapper::repository)
          .toList();

      int totalCount = Integer.parseInt(Objects.requireNonNullElse(response.getHeaders().get("X-Total-Count"), List.of("0")).getFirst());

      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.LINK, LinkHeaderProcessor.create(baseURI(request), limit, page, totalCount).toHeader());

      log.info("Found repositories for organization {} at page {}", org, page);
      return ResponseEntity.ok()
          .headers(headers)
          .body(repositories);
    } catch (HttpClientErrorException.NotFound _) {
      log.info("No repositories found for organization {}", org);
      return ResponseEntity.ok(Collections.emptyList());
    }
  }

  @GetMapping("/members")
  ResponseEntity<List<GitHubUserResponse>> members(
      HttpServletRequest request,
      @PathVariable String org,
      @RequestParam(value = "per_page", defaultValue = "30") Integer limit,
      @RequestParam(value = "page", defaultValue = "1") Integer page
  ) {
    try {
      log.info("Querying members for organization {} at page {}", org, page);
      ResponseEntity<List<User>> response = giteaClient.orgListMembers(org, page, limit);
      if (!response.hasBody()) {
        log.info("No members found for organization {} at page {}", org, page);
        return ResponseEntity.ok(Collections.emptyList());
      }

      int totalCount = Integer.parseInt(Objects.requireNonNullElse(response.getHeaders().get("X-Total-Count"), List.of("0")).getFirst());

      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.LINK, LinkHeaderProcessor.create(baseURI(request), limit, page, totalCount).toHeader());

      log.info("Found members for organization {} at page {}", org, page);
      return ResponseEntity.ok()
          .headers(headers)
          .body(userMapper.map(new ArrayList<>(response.getBody())));
    } catch (HttpClientErrorException.NotFound _) {
      log.info("No members found for organization {}", org);
      return ResponseEntity.ok(Collections.emptyList());
    }
  }

  private static URI baseURI(HttpServletRequest request) {
    return UriComponentsBuilder.newInstance()
        .scheme(request.getScheme())
        .host(request.getServerName())
        .port(request.getServerPort())
        .path(request.getRequestURI())
        .build()
        .toUri();
  }

  private GitHubOrganizationResponse map(Organization organization) {
    URI htmlUrl = organization.name() == null
        ? null
        : UriComponentsBuilder.fromUriString(giteaConfigurationProperties.baseUrl())
            .pathSegment(organization.name())
            .build()
            .toUri();

    return new GitHubOrganizationResponse(
        organization.name(),
        organization.id(),
        organization.description(),
        organization.avatarUrl() == null ? null : URI.create(organization.avatarUrl()),
        htmlUrl,
        "Organization"
    );
  }

}
