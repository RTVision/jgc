package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.BranchProtection;
import hu.alphabox.jgc.github.GitHubTokenFetcher;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class GitHubBranchProtectionCallHandlerTest {

  @Autowired
  private RestTestClient client;

  @Autowired
  private GitHubAppInstallationManager installationManager;

  @MockitoBean
  private GiteaClient giteaClient;

  @MockitoBean
  private AllowedRepositoryService allowedRepositoryService;

  private static BranchProtection rule(String branchName, String ruleName) {
    return new BranchProtection(
        branchName,
        ruleName,
        null, null, null,
        2,
        null, null, null, null,
        true,
        true,
        true,
        true,
        List.of("ci/build"),
        null,
        null,
        null
    );
  }

  private String token() {
    return new GitHubTokenFetcher(client, installationManager.getAppInstallation().orElseThrow()).fetchServerToken();
  }

  @Test
  void shouldReturnBranchProtectionForExactMatch() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    Mockito.when(giteaClient.repoListBranchProtections("TestOrg", "TestRepo"))
        .thenReturn(ResponseEntity.ok(List.of(rule("main", "main"))));

    var result = client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/branches/main/protection")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully()
        .returnResult(GitHubBranchProtectionResponse.class)
        .getResponseBody();

    Assertions.assertThat(result.requiredPullRequestReviews().requiredApprovingReviewCount()).isEqualTo(2);
    Assertions.assertThat(result.requiredStatusChecks().strict()).isTrue();
    Assertions.assertThat(result.requiredStatusChecks().contexts()).containsExactly("ci/build");
    Assertions.assertThat(result.requiredSignatures().enabled()).isTrue();
  }

  @Test
  void shouldMatchGlobRuleName() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    Mockito.when(giteaClient.repoListBranchProtections("TestOrg", "TestRepo"))
        .thenReturn(ResponseEntity.ok(List.of(rule(null, "release-*"))));

    client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/branches/release-1.0/protection")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully();
  }

  @Test
  void shouldReturnNotFoundWhenNoRuleMatches() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    Mockito.when(giteaClient.repoListBranchProtections("TestOrg", "TestRepo"))
        .thenReturn(ResponseEntity.ok(List.of()));

    client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/branches/main/protection")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchange()
        .expectStatus().isNotFound();
  }

  @Test
  void shouldReturnNotFoundForDisallowedRepository() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(false);

    client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/branches/main/protection")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
  }

}
