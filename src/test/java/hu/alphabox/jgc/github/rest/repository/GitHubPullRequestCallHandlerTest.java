package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.ChangedFile;
import hu.alphabox.jgc.gitea.client.model.PRBranchInfo;
import hu.alphabox.jgc.gitea.client.model.PullRequest;
import hu.alphabox.jgc.gitea.client.model.Repository;
import hu.alphabox.jgc.gitea.client.model.User;
import hu.alphabox.jgc.github.GitHubTokenFetcher;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import java.net.URI;
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
class GitHubPullRequestCallHandlerTest {

  @Autowired
  private RestTestClient client;

  @Autowired
  private GitHubAppInstallationManager installationManager;

  @MockitoBean
  private GiteaClient giteaClient;

  @MockitoBean
  private AllowedRepositoryService allowedRepositoryService;

  private static Repository repository(String fullName) {
    return new Repository(
        null, null, null, null, null, null, null, null, null,
        fullName,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        List.of(),
        null, null, null
    );
  }

  private static PullRequest pullRequest() {
    return new PullRequest(
        null,
        new PRBranchInfo("main", repository("TestOrg/TestRepo"), "base-sha"),
        "pr body",
        null,
        null,
        null,
        null,
        null,
        new PRBranchInfo("feature", repository("TestOrg/TestRepo"), "head-sha"),
        "https://gitea-base-url.test/TestOrg/TestRepo/pulls/1",
        1L,
        "merge-sha",
        true,
        null,
        null,
        1L,
        List.of(),
        "open",
        "PR title",
        null,
        new User(null, null, null, null, null, null, 5L, null, null, "octocat")
    );
  }

  private String token() {
    return new GitHubTokenFetcher(client, installationManager.getAppInstallation().orElseThrow()).fetchServerToken();
  }

  @Test
  void shouldListPullRequestsWithLinkHeader() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    Mockito.when(giteaClient.repoListPullRequests(
        Mockito.eq("TestOrg"), Mockito.eq("TestRepo"), Mockito.any(), Mockito.anyString(),
        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.anyInt()
    )).thenReturn(
        ResponseEntity.ok()
            .header("X-Total-Count", "1")
            .body(List.of(pullRequest()))
    );

    var result = client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/pulls")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully()
        .returnResult(GitHubPullRequestResponse[].class);

    GitHubPullRequestResponse[] body = result.getResponseBody();
    Assertions.assertThat(body).hasSize(1);
    GitHubPullRequestResponse pr = body[0];
    Assertions.assertThat(pr.number()).isEqualTo(1L);
    Assertions.assertThat(pr.state()).isEqualTo("open");
    Assertions.assertThat(pr.base().ref()).isEqualTo("main");
    Assertions.assertThat(pr.base().repo().fullName()).isEqualTo("TestOrg/TestRepo");
    Assertions.assertThat(pr.head().ref()).isEqualTo("feature");
  }

  @Test
  void shouldReturnSinglePullRequestWithMergedFlag() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    Mockito.when(giteaClient.repoGetPullRequest("TestOrg", "TestRepo", 1L))
        .thenReturn(ResponseEntity.ok(pullRequest()));

    var result = client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/pulls/1")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully()
        .returnResult(GitHubPullRequestResponse.class);

    Assertions.assertThat(result.getResponseBody().merged()).isTrue();
    Assertions.assertThat(result.getResponseBody().mergeCommitSha()).isEqualTo("merge-sha");
  }

  @Test
  void shouldReturnChangedFilesForPullRequest() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    Mockito.when(giteaClient.repoGetPullRequestFiles(Mockito.eq("TestOrg"), Mockito.eq("TestRepo"), Mockito.eq(1L), Mockito.anyInt(), Mockito.anyInt()))
        .thenReturn(ResponseEntity.ok(List.of(new ChangedFile(
            "src/Main.java",
            null,
            "modified",
            5L,
            2L,
            7L,
            "https://gitea.test/raw/src/Main.java",
            "https://gitea.test/contents/src/Main.java",
            null,
            "@@ -1,2 +1,5 @@ patch content"
        ))));

    var result = client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/pulls/1/files")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully()
        .returnResult(GitHubPullRequestFileResponse[].class)
        .getResponseBody();

    Assertions.assertThat(result).hasSize(1);
    Assertions.assertThat(result[0].filename()).isEqualTo("src/Main.java");
    Assertions.assertThat(result[0].status()).isEqualTo("modified");
    Assertions.assertThat(result[0].changes()).isEqualTo(7L);
    Assertions.assertThat(result[0].patch()).isEqualTo("@@ -1,2 +1,5 @@ patch content");
  }

  @Test
  void shouldReturnNotFoundForDisallowedRepository() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(false);

    client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/pulls/1")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
  }

}
