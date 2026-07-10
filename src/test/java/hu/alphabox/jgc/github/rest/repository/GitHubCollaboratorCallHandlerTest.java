package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.RepoCollaboratorPermission;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class GitHubCollaboratorCallHandlerTest {

  @Autowired
  private RestTestClient client;

  @Autowired
  private GitHubAppInstallationManager installationManager;

  @MockitoBean
  private GiteaClient giteaClient;

  @MockitoBean
  private AllowedRepositoryService allowedRepositoryService;

  private String token() {
    return new GitHubTokenFetcher(client, installationManager.getAppInstallation().orElseThrow()).fetchServerToken();
  }

  @Test
  void shouldListCollaboratorsWithLinkHeader() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    Mockito.when(giteaClient.repoListCollaborators(Mockito.eq("TestOrg"), Mockito.eq("TestRepo"), Mockito.anyInt(), Mockito.anyInt()))
        .thenReturn(
            ResponseEntity.ok()
                .header("X-Total-Count", "1")
                .body(List.of(new User(null, null, null, null, null, null, 3L, null, null, "octocat")))
        );

    var result = client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/collaborators")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully();

    Assertions.assertThat(result.returnResult(Object[].class).getResponseHeaders().get(HttpHeaders.LINK)).isNotNull();
  }

  @Test
  void shouldReturnCollaboratorPermission() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    Mockito.when(giteaClient.repoGetCollaboratorPermission("TestOrg", "TestRepo", "octocat"))
        .thenReturn(ResponseEntity.ok(new RepoCollaboratorPermission(
            "owner",
            "Owner",
            new User(null, null, null, null, null, null, 3L, null, null, "octocat")
        )));

    var result = client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/collaborators/octocat/permission")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully()
        .returnResult(GitHubCollaboratorPermissionResponse.class)
        .getResponseBody();

    Assertions.assertThat(result.permission()).isEqualTo("admin");
    Assertions.assertThat(result.user().login()).isEqualTo("octocat");
  }

  @Test
  void shouldReturnNotFoundForDisallowedRepository() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(false);

    client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/collaborators")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
  }

}
