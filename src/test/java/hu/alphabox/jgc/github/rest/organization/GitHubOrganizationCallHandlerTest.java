package hu.alphabox.jgc.github.rest.organization;

import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.Organization;
import hu.alphabox.jgc.gitea.client.model.Repository;
import hu.alphabox.jgc.gitea.client.model.User;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class GitHubOrganizationCallHandlerTest {

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

  private String token() {
    return new GitHubTokenFetcher(client, installationManager.getAppInstallation().orElseThrow()).fetchServerToken();
  }

  @Test
  void shouldReturnOrganization() {
    Mockito.when(giteaClient.orgGet("TestOrg"))
        .thenReturn(ResponseEntity.ok(new Organization("https://gitea-base-url.test/avatar/1", "an org", 1L, "TestOrg")));

    var result = client.get()
        .uri("/api/v3/orgs/TestOrg")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully()
        .returnResult(GitHubOrganizationResponse.class)
        .getResponseBody();

    Assertions.assertThat(result.login()).isEqualTo("TestOrg");
    Assertions.assertThat(result.type()).isEqualTo("Organization");
    Assertions.assertThat(result.description()).isEqualTo("an org");
  }

  @Test
  void shouldListAllowedOrganizationRepositoriesWithLinkHeader() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg/AllowedRepo")).thenReturn(true);
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg/BlockedRepo")).thenReturn(false);
    Mockito.when(giteaClient.orgListRepos(Mockito.eq("TestOrg"), Mockito.anyInt(), Mockito.anyInt()))
        .thenReturn(
            ResponseEntity.ok()
                .header("X-Total-Count", "2")
                .body(List.of(repository("TestOrg/AllowedRepo"), repository("TestOrg/BlockedRepo")))
        );

    var result = client.get()
        .uri("/api/v3/orgs/TestOrg/repos")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully();

    Assertions.assertThat(result.returnResult(Object[].class).getResponseHeaders().get(HttpHeaders.LINK)).isNotNull();
  }

  @Test
  void shouldListOrganizationMembers() {
    Mockito.when(giteaClient.orgListMembers(Mockito.eq("TestOrg"), Mockito.anyInt(), Mockito.anyInt()))
        .thenReturn(
            ResponseEntity.ok()
                .header("X-Total-Count", "1")
                .body(List.of(new User(null, null, null, null, null, null, 7L, null, null, "octocat")))
        );

    var result = client.get()
        .uri("/api/v3/orgs/TestOrg/members")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully();

    Assertions.assertThat(result.returnResult(Object[].class).getResponseHeaders().get(HttpHeaders.LINK)).isNotNull();
  }

}
