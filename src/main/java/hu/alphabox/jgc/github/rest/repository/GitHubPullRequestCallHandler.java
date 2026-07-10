package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.LinkHeaderProcessor;
import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.PullReview;
import hu.alphabox.jgc.gitea.client.model.PullRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
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
@RequestMapping("/api/v3/repos/{owner}/{repo}/pulls")
@RequiredArgsConstructor
class GitHubPullRequestCallHandler {

  private final GiteaClient giteaClient;
  private final GitHubPullRequestMapper mapper;
  private final AllowedRepositoryService allowedRepositoryService;

  @GetMapping
  ResponseEntity<List<GitHubPullRequestResponse>> pulls(
      HttpServletRequest request,
      @PathVariable String owner,
      @PathVariable String repo,
      @RequestParam(value = "state", defaultValue = "open") String state,
      @RequestParam(value = "per_page", defaultValue = "30") Integer limit,
      @RequestParam(value = "page", defaultValue = "1") Integer page
  ) {
    if (!allowedRepositoryService.isAllowed(owner, repo)) {
      return ResponseEntity.notFound().build();
    }

    try {
      log.info("Querying for pull requests in {}/{} with state {} at page {}", owner, repo, state, page);
      ResponseEntity<List<PullRequest>> response = giteaClient.repoListPullRequests(owner, repo, null, state, null, null, null, null, page, limit);
      if (!response.hasBody()) {
        log.info("No pull requests found in {}/{} at page {}", owner, repo, page);
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

      log.info("Successfully returned pull requests in {}/{} at page {}", owner, repo, page);
      return ResponseEntity.ok()
          .headers(headers)
          .body(mapper.pullRequests(response.getBody()));
    } catch (HttpClientErrorException.NotFound _) {
      log.info("No pull requests found in {}/{} at page {}", owner, repo, page);
      return ResponseEntity.ok(Collections.emptyList());
    }
  }

  @GetMapping("/{pull_number}")
  ResponseEntity<GitHubPullRequestResponse> pull(
      @PathVariable String owner,
      @PathVariable String repo,
      @PathVariable("pull_number") Long pullNumber
  ) {
    if (!allowedRepositoryService.isAllowed(owner, repo)) {
      return ResponseEntity.notFound().build();
    }

    try {
      log.info("Querying for pull request {} in {}/{}", pullNumber, owner, repo);
      ResponseEntity<PullRequest> response = giteaClient.repoGetPullRequest(owner, repo, pullNumber);
      log.info("Pull request {} found in {}/{}", pullNumber, owner, repo);
      return ResponseEntity.ok(mapper.pullRequest(response.getBody()));
    } catch (HttpClientErrorException.NotFound _) {
      log.info("Pull request {} not found in {}/{}", pullNumber, owner, repo);
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping("/{pull_number}/reviews")
  ResponseEntity<List<GitHubPullRequestReviewResponse>> reviews(
      HttpServletRequest request,
      @PathVariable String owner,
      @PathVariable String repo,
      @PathVariable("pull_number") Long pullNumber,
      @RequestParam(value = "per_page", defaultValue = "30") Integer limit,
      @RequestParam(value = "page", defaultValue = "1") Integer page
  ) {
    if (!allowedRepositoryService.isAllowed(owner, repo)) {
      return ResponseEntity.notFound().build();
    }

    try {
      log.info("Querying for pull request reviews for pull number: {} in {}/{} at page {}", pullNumber, owner, repo, page);
      ResponseEntity<List<PullReview>> response = giteaClient.repoListPullReviews(owner, repo, pullNumber, page, limit);
      if (!response.hasBody()) {
        log.info("No pull request reviews found for pull number: {} in {}/{} at page {}", pullNumber, owner, repo, page);
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

      log.info("Successfully returned pull request reviews for pull number: {} in {}/{} at page {}", pullNumber, owner, repo, page);
      return ResponseEntity.ok()
          .headers(headers)
          .body(mapper.reviews(response.getBody()));
    } catch (HttpClientErrorException.NotFound _) {
      log.info("No pull request reviews found for pull number: {} in {}/{} at page {}", pullNumber, owner, repo, page);
      return ResponseEntity.ok(Collections.emptyList());
    }
  }

  @GetMapping("/{pull_number}/requested_reviewers")
  ResponseEntity<GitHubPullRequestRequestedReviewersResponse> requestedReviewers(
      @PathVariable String owner,
      @PathVariable String repo,
      @PathVariable("pull_number") Long pullNumber
  ) {
    if (!allowedRepositoryService.isAllowed(owner, repo)) {
      return ResponseEntity.notFound().build();
    }

    try {
      log.info("Querying for pull request requested reviewers for pull number: {} in {}/{}", pullNumber, owner, repo);
      ResponseEntity<List<PullReview>> response = giteaClient.repoListPullReviews(owner, repo, pullNumber, null, null);
      if (!response.hasBody()) {
        log.info("No pull request requested reviewers found for pull number: {} in {}/{}", pullNumber, owner, repo);
        return ResponseEntity.ok(new GitHubPullRequestRequestedReviewersResponse(Collections.emptyList(), Collections.emptyList()));
      }
      log.info("Pull request requested reviewers found for pull number: {} in {}/{}", pullNumber, owner, repo);
      return ResponseEntity.ok(mapper.map(response.getBody()));
    } catch (HttpClientErrorException.NotFound _) {
      log.info("No pull request requested reviewers found for pull number: {} in {}/{}", pullNumber, owner, repo);
      return ResponseEntity.ok(new GitHubPullRequestRequestedReviewersResponse(Collections.emptyList(), Collections.emptyList()));
    }
  }

  @GetMapping("/{pull_number}/comments")
  ResponseEntity<List<GitHubPullRequestReviewCommentResponse>> comments(
      @PathVariable String owner,
      @PathVariable String repo,
      @PathVariable("pull_number") Long pullNumber
  ) {
    if (!allowedRepositoryService.isAllowed(owner, repo)) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(Collections.emptyList());
  }

}
