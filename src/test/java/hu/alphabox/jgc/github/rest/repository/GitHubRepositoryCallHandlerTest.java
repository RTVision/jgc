package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.github.GitHubTokenFetcher;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import java.util.Map;
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
class GitHubRepositoryCallHandlerTest {

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
  void shouldReturnLanguages() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(true);
    Mockito.when(giteaClient.repoGetLanguages("TestOrg", "TestRepo"))
        .thenReturn(ResponseEntity.ok(Map.of("Java", 12345L, "HTML", 42L)));

    var result = client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/languages")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully()
        .returnResult(Map.class)
        .getResponseBody();

    Assertions.assertThat(result).containsEntry("Java", 12345).containsEntry("HTML", 42);
  }

  @Test
  void shouldReturnNotFoundForDisallowedRepositoryLanguages() {
    Mockito.when(allowedRepositoryService.isAllowed("TestOrg", "TestRepo")).thenReturn(false);

    client.get()
        .uri("/api/v3/repos/TestOrg/TestRepo/languages")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
  }

}
