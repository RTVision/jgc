package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.CommitMeta;
import hu.alphabox.jgc.gitea.client.model.CompareResult;
import hu.alphabox.jgc.gitea.client.model.GitTreeResponse;
import hu.alphabox.jgc.gitea.client.model.PRBranchInfo;
import hu.alphabox.jgc.gitea.client.model.PullRequest;
import hu.alphabox.jgc.gitea.client.model.Reference;
import hu.alphabox.jgc.gitea.client.model.RepoCommit;
import hu.alphabox.jgc.gitea.client.model.Repository;
import hu.alphabox.jgc.gitea.client.model.User;
import hu.alphabox.jgc.github.GitHubTokenFetcher;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.web.client.HttpClientErrorException;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class GitHubRefCallHandlerTest {

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

  private static hu.alphabox.jgc.gitea.client.model.Commit commit(String sha) {
    return new hu.alphabox.jgc.gitea.client.model.Commit(
        null,
        new RepoCommit(
            new hu.alphabox.jgc.gitea.client.model.CommitUser("2024-01-01T00:00:00Z", "octocat@example.com", "The Octocat"),
            null,
            "Commit message",
            new CommitMeta(sha, null),
            null,
            null
        ),
        null,
        null,
        Collections.emptyList(),
        "https://gitea.test/TestOrg/TestRepo/commit/%s".formatted(sha),
        Collections.emptyList(),
        sha,
        null,
        null
    );
  }

  private String token() {
    return new GitHubTokenFetcher(client, installationManager.getAppInstallation().orElseThrow()).fetchServerToken();
  }

  @Test
  void shouldHandleEncodedGitRefRequest() throws URISyntaxException {
    ArgumentCaptor<String> refCaptor = ArgumentCaptor.forClass(String.class);

    Mockito
        .when(giteaClient.repoListGitRefs(Mockito.anyString(), Mockito.anyString(), refCaptor.capture()))
        .thenReturn(ResponseEntity.ok(List.of(new Reference(null, "refs/heads/release-1.0.0", null))));

    GitHubTokenFetcher tokenFetcher = new GitHubTokenFetcher(client, installationManager.getAppInstallation().orElseThrow());
    String serverToken = tokenFetcher.fetchServerToken();

    client.get()
        .uri(new URI("/api/v3/repos/TestOrg/TestRepo/git/ref/heads%2Frelease-1.0.0"))
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(serverToken))
        .exchangeSuccessfully();

    Assertions.assertThat(refCaptor.getValue())
        .isEqualTo("heads/release-1.0.0");
  }

  @Test
  void shouldListCommitsWithoutFallingIntoSingleCommitHandler() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    hu.alphabox.jgc.gitea.client.model.Commit listed = commit("abc123");
    // Gitea omits the affected-file list when commits are listed with files=false
    listed = new hu.alphabox.jgc.gitea.client.model.Commit(
        listed.author(), listed.commit(), listed.committer(), listed.created(), null,
        listed.htmlUrl(), listed.parents(), listed.sha(), listed.stats(), listed.url()
    );
    Mockito.when(giteaClient.repoGetAllCommits(
            Mockito.eq("TestOrg"), Mockito.eq("TestRepo"), Mockito.isNull(), Mockito.isNull(),
            Mockito.eq(false), Mockito.eq(false), Mockito.eq(false), Mockito.eq(1), Mockito.eq(1), Mockito.isNull()))
        .thenReturn(ResponseEntity.ok(List.of(listed)));

    var result = client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/commits?per_page=1")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully()
        .returnResult(GitHubCommitResponse[].class)
        .getResponseBody();

    Assertions.assertThat(result).hasSize(1);
    Assertions.assertThat(result[0].sha()).isEqualTo("abc123");
    Assertions.assertThat(result[0].files()).isEmpty();
    Mockito.verify(giteaClient, Mockito.never()).repoGetSingleCommit(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  void shouldNotListCommitsOfDisallowedRepository() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(false);

    client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/commits")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchange()
        .expectStatus().isNotFound();
  }

  @Test
  void shouldCompareCommits() throws URISyntaxException {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    Mockito.when(giteaClient.repoCompare(Mockito.eq("TestOrg"), Mockito.eq("TestRepo"), Mockito.eq("main...feature")))
        .thenReturn(ResponseEntity.ok(new CompareResult(1L, List.of(commit("abc123")))));

    var result = client.get()
        .uri(new URI("/api/v3/repos/TestOrg/TestRepo/compare/main...feature"))
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully()
        .returnResult(GitHubCompareResponse.class)
        .getResponseBody();

    Assertions.assertThat(result.totalCommits()).isEqualTo(1L);
    Assertions.assertThat(result.commits()).hasSize(1);
    Assertions.assertThat(result.commits().getFirst().sha()).isEqualTo("abc123");
    Assertions.assertThat(result.commits().getFirst().commit().message()).isEqualTo("Commit message");
    Assertions.assertThat(result.commits().getFirst().commit().author().name()).isEqualTo("The Octocat");
    Assertions.assertThat(result.commits().getFirst().commit().author().email()).isEqualTo("octocat@example.com");
  }

  @Test
  void shouldReturnGitTreeWithRecursiveFlagPropagated() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    Mockito.when(giteaClient.repoGetGitTree(Mockito.eq("TestOrg"), Mockito.eq("TestRepo"), Mockito.eq("abc123"), Mockito.eq(true), Mockito.any(), Mockito.any()))
        .thenReturn(ResponseEntity.ok(new GitTreeResponse(
            "abc123",
            "https://gitea.test/api/v1/repos/TestOrg/TestRepo/git/trees/abc123",
            List.of(new GitTreeResponse.Entry("src/Main.java", "100644", "blob", "def456", 123L, "https://gitea.test/blob/def456")),
            false,
            1,
            1
        )));

    var result = client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/git/trees/abc123?recursive=true")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully()
        .returnResult(GitHubTreeResponse.class)
        .getResponseBody();

    Assertions.assertThat(result.sha()).isEqualTo("abc123");
    Assertions.assertThat(result.tree()).hasSize(1);
    Assertions.assertThat(result.tree().getFirst().path()).isEqualTo("src/Main.java");
    Assertions.assertThat(result.truncated()).isFalse();
  }

  @Test
  void shouldReturnPullRequestsAssociatedWithCommitTakingPriorityOverWildcardCommitHandler() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    Mockito.when(giteaClient.repoGetCommitPullRequest(Mockito.eq("TestOrg"), Mockito.eq("TestRepo"), Mockito.eq("abc123")))
        .thenReturn(ResponseEntity.ok(new PullRequest(
            null,
            new PRBranchInfo("main", repository("TestOrg/TestRepo"), "base-sha"),
            "pr body",
            null, null, null, null, null,
            new PRBranchInfo("feature", repository("TestOrg/TestRepo"), "head-sha"),
            "https://gitea-base-url.test/TestOrg/TestRepo/pulls/1",
            1L,
            "merge-sha",
            true,
            null, null,
            1L,
            List.of(),
            "open",
            "PR title",
            null,
            new User(null, null, null, null, null, null, 5L, null, null, "octocat")
        )));

    var result = client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/commits/abc123/pulls")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully()
        .returnResult(GitHubPullRequestResponse[].class)
        .getResponseBody();

    Assertions.assertThat(result).hasSize(1);
    Assertions.assertThat(result[0].number()).isEqualTo(1L);

    // handleCommit (the /commits/** wildcard) must not have been invoked for this more specific path
    Mockito.verify(giteaClient, Mockito.never()).repoGetSingleCommit(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  void shouldReturnEmptyArrayWhenNoPullRequestAssociatedWithCommit() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    Mockito.when(giteaClient.repoGetCommitPullRequest(Mockito.eq("TestOrg"), Mockito.eq("TestRepo"), Mockito.eq("abc123")))
        .thenThrow((HttpClientErrorException) HttpClientErrorException.create(
            org.springframework.http.HttpStatus.NOT_FOUND, "Not Found", org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

    var result = client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/commits/abc123/pulls")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully()
        .returnResult(GitHubPullRequestResponse[].class)
        .getResponseBody();

    Assertions.assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnOkForHeadRequestAgainstContentsEndpoint() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    Mockito.when(giteaClient.repoGetContents(Mockito.eq("TestOrg"), Mockito.eq("TestRepo"), Mockito.eq("README.md"), Mockito.any()))
        .thenReturn(ResponseEntity.ok(new hu.alphabox.jgc.gitea.client.model.ContentsResponse(
            null, null, null, null, null, null, "README.md", "README.md", "sha123", 10L, "file", null
        )));

    client.head()
        .uri("/api/v3/repos/TestOrg/TestRepo/contents/README.md")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.OK)
        .expectBody().isEmpty();
  }

}
