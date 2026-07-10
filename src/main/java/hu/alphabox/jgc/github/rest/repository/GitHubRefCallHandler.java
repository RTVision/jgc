package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.Branch;
import hu.alphabox.jgc.gitea.client.model.Commit;
import hu.alphabox.jgc.gitea.client.model.CreateBranchRepoOption;
import hu.alphabox.jgc.gitea.client.model.Reference;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@RestController
@RequestMapping("/api/v3/repos/{owner}/{repo}")
@RequiredArgsConstructor
class GitHubRefCallHandler {

  private final GiteaClient giteaClient;
  private final GitHubRefMapper mapper;
  private final AllowedRepositoryService allowedRepositoryService;

  @GetMapping("/branches")
  ResponseEntity<List<GitHubBranchResponse>> handleBranches(@PathVariable String owner, @PathVariable String repo) {
    if (!allowedRepositoryService.isAllowed(owner, repo)) {
      return ResponseEntity.notFound().build();
    }

    log.info("Querying branches for repository {}/{}", owner, repo);
    ResponseEntity<List<Branch>> branches = giteaClient.repoListBranches(owner, repo, 1, 100);
    if (!branches.hasBody() || HttpStatus.NOT_FOUND == branches.getStatusCode()) {
      log.info("No branches found for repository {}/{}", owner, repo);
      return ResponseEntity.notFound().build();
    }
    log.info("Found branches for repository {}/{}", owner, repo);
    return ResponseEntity.ok(mapper.branches(branches.getBody()));
  }

  @GetMapping("/branches/{branch}")
  ResponseEntity<GitHubBranchResponse> handleBranch(
      @PathVariable String owner,
      @PathVariable String repo,
      @PathVariable String branch
  ) {
    if (!allowedRepositoryService.isAllowed(owner, repo)) {
      return ResponseEntity.notFound().build();
    }

    try {
      log.info("Querying branch {} for repository {}/{}", branch, owner, repo);
      ResponseEntity<Branch> response = giteaClient.repoGetBranch(owner, repo, branch);
      log.info("Found branch {} for repository {}/{}", branch, owner, repo);
      return ResponseEntity.ok(mapper.branch(response.getBody()));
    } catch (HttpClientErrorException.NotFound _) {
      log.info("Branch {} not found for repository {}/{}", branch, owner, repo);
      return ResponseEntity.notFound().build();
    }
  }

  @PostMapping("/git/refs")
  ResponseEntity<GitHubRefResponse> handleGitRefCreation(
      @PathVariable String owner,
      @PathVariable String repo,
      @RequestBody GitHubCreateRefRequest request
  ) {
    String[] refParts = request.ref().split("refs/heads/", 2);

    String branchName = refParts[refParts.length - 1];
    CreateBranchRepoOption giteaRequest = new CreateBranchRepoOption(
        branchName,
        request.sha()
    );

    log.info("Creating branch with name: {}, sha: {} in {}/{}, ", branchName, request.sha(), owner, repo);
    try {
      ResponseEntity<Branch> response = giteaClient.repoCreateBranch(owner, repo, giteaRequest);
      log.info("Successfully created branch with name: {}, sha: {} in {}/{}, ", branchName, request.sha(), owner, repo);
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(mapper.refFromBranch(request.ref(), response.getBody()));
    } catch (HttpClientErrorException e) {
      log.error("Failed to create branch with name: {}, sha: {} in {}/{}, ", branchName, request.sha(), owner, repo, e);
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
  }

  @GetMapping("/git/ref/**")
  ResponseEntity<GitHubRefResponse> handleRef(
      HttpServletRequest request,
      @PathVariable String owner,
      @PathVariable String repo
  ) {
    String ref = URLDecoder.decode(request.getRequestURI().split("/ref/", 2)[1], StandardCharsets.UTF_8);
    try {
      log.info("Check ref {} exists in {}/{}", ref, owner, repo);
      ResponseEntity<List<Reference>> response = giteaClient.repoListGitRefs(owner, repo, ref);
      if (!response.hasBody()) {
        return ResponseEntity.notFound().build();
      }
      List<Reference> references = response.getBody();
      String prefixedRef = "refs/" + ref;

      return references.stream()
          .filter(reference -> prefixedRef.equals(reference.ref()))
          .findFirst()
          .map(reference -> {
            log.info("Reference {} found in {}/{}.", prefixedRef, owner, repo);
            return ResponseEntity.ok(mapper.ref(reference));
          })
          .orElseGet(() -> {
            log.info("Reference {} not found in {}/{}. Found references: {}", prefixedRef, owner, repo, references.stream().map(Reference::ref).toList());
            return ResponseEntity.notFound().build();
          });
    } catch (HttpClientErrorException.NotFound _) {
      log.info("Reference {} not found in {}/{} in Gitea.", ref, owner, repo);
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping("/commits/**")
  ResponseEntity<GitHubCommitResponse> handleCommit(
      HttpServletRequest request,
      @PathVariable String owner,
      @PathVariable String repo
  ) {
    String ref = request.getRequestURI().split("/commits/", 2)[1];
    try {
      log.info("Looking for commit by ref: {} in {}/{}, ", ref, owner, repo);
      ResponseEntity<Commit> giteaCommit = giteaClient.repoGetSingleCommit(owner, repo, ref, false, false, true);
      log.info("Commit found by ref: {} in {}/{}, ", ref, owner, repo);
      return ResponseEntity.ok(mapper.commit(giteaCommit.getBody()));

    } catch (HttpClientErrorException.NotFound _) {
      log.info("Commit not found by ref: {} in {}/{}, ", ref, owner, repo);
      return ResponseEntity.notFound().build();
    }
  }
}
