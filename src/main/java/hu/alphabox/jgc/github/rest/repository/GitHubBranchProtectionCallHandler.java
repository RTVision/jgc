package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.BranchProtection;
import hu.alphabox.jgc.github.rest.repository.GitHubBranchProtectionResponse.AllowDeletions;
import hu.alphabox.jgc.github.rest.repository.GitHubBranchProtectionResponse.AllowForcePushes;
import hu.alphabox.jgc.github.rest.repository.GitHubBranchProtectionResponse.EnforceAdmins;
import hu.alphabox.jgc.github.rest.repository.GitHubBranchProtectionResponse.RequiredPullRequestReviews;
import hu.alphabox.jgc.github.rest.repository.GitHubBranchProtectionResponse.RequiredSignatures;
import hu.alphabox.jgc.github.rest.repository.GitHubBranchProtectionResponse.RequiredStatusChecks;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
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
@RequestMapping("/api/v3/repos/{owner}/{repo}/branches/{branch}/protection")
@RequiredArgsConstructor
class GitHubBranchProtectionCallHandler {

  private final GiteaClient giteaClient;
  private final AllowedRepositoryService allowedRepositoryService;

  @GetMapping
  ResponseEntity<GitHubBranchProtectionResponse> protection(
      @PathVariable String owner,
      @PathVariable String repo,
      @PathVariable String branch
  ) {
    if (!allowedRepositoryService.isAllowed(owner, repo)) {
      return ResponseEntity.notFound().build();
    }

    try {
      log.info("Querying branch protections for branch {} in {}/{}", branch, owner, repo);
      ResponseEntity<List<BranchProtection>> response = giteaClient.repoListBranchProtections(owner, repo);
      List<BranchProtection> rules = response.hasBody() ? response.getBody() : Collections.emptyList();

      return findMatchingRule(rules, branch)
          .map(GitHubBranchProtectionCallHandler::map)
          .map(protectionResponse -> {
            log.info("Found branch protection rule for branch {} in {}/{}", branch, owner, repo);
            return ResponseEntity.ok(protectionResponse);
          })
          .orElseGet(() -> {
            log.info("No branch protection rule matched branch {} in {}/{}", branch, owner, repo);
            return ResponseEntity.notFound().build();
          });
    } catch (HttpClientErrorException.NotFound _) {
      log.info("No branch protections found for {}/{}", owner, repo);
      return ResponseEntity.notFound().build();
    }
  }

  private static Optional<BranchProtection> findMatchingRule(List<BranchProtection> rules, String branch) {
    Optional<BranchProtection> exactMatch = rules.stream()
        .filter(rule -> branch.equals(rule.branchName()) || branch.equals(rule.ruleName()))
        .findFirst();

    if (exactMatch.isPresent()) {
      return exactMatch;
    }

    return rules.stream()
        .filter(rule -> rule.ruleName() != null && rule.ruleName().contains("*"))
        .filter(rule -> globToPattern(rule.ruleName()).matcher(branch).matches())
        .max(Comparator.comparingInt(rule -> rule.ruleName().length()));
  }

  private static Pattern globToPattern(String glob) {
    StringBuilder regex = new StringBuilder();
    for (char c : glob.toCharArray()) {
      switch (c) {
        case '*' -> regex.append(".*");
        case '.', '(', ')', '+', '|', '^', '$', '@', '%', '[', ']', '{', '}', '\\' -> regex.append('\\').append(c);
        default -> regex.append(c);
      }
    }
    return Pattern.compile("^" + regex + "$");
  }

  private static GitHubBranchProtectionResponse map(BranchProtection rule) {
    RequiredPullRequestReviews requiredPullRequestReviews = null;
    if (rule.requiredApprovals() != null && rule.requiredApprovals() > 0) {
      requiredPullRequestReviews = new RequiredPullRequestReviews(
          rule.requiredApprovals(),
          Boolean.TRUE.equals(rule.dismissStaleApprovals()),
          false
      );
    }

    RequiredStatusChecks requiredStatusChecks = null;
    if (Boolean.TRUE.equals(rule.enableStatusCheck())) {
      requiredStatusChecks = new RequiredStatusChecks(
          Boolean.TRUE.equals(rule.blockOnOutdatedBranch()),
          rule.statusCheckContexts()
      );
    }

    return new GitHubBranchProtectionResponse(
        requiredPullRequestReviews,
        requiredStatusChecks,
        new EnforceAdmins(false),
        new RequiredSignatures(Boolean.TRUE.equals(rule.requireSignedCommits())),
        new AllowForcePushes(false),
        new AllowDeletions(false)
    );
  }

}
