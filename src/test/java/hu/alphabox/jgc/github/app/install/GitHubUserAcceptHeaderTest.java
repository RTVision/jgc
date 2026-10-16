package hu.alphabox.jgc.github.app.install;

import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.User;
import hu.alphabox.jgc.github.GitHubUserAccessTokenService;
import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class GitHubUserAcceptHeaderTest {

  @Autowired
  private RestTestClient client;

  @Autowired
  private GitHubAppInstallationManager installationManager;

  @Autowired
  private GitHubUserAccessTokenService userTokenService;

  @MockitoBean
  private GiteaClient giteaClient;

  private String token() {
    GitHubAppInstallation appInstallation = installationManager.getAppInstallation().orElseThrow();
    String code = userTokenService.generateTemporaryCode();
    return userTokenService.generateToken(appInstallation.getClientId(), appInstallation.getClientSecret(), code);
  }

  /**
   * GitHub serves /user whatever JSON media type the client asks for. Sentry's GitHub Enterprise
   * integration asks for plain application/json, which a v3+json-only mapping rejected with 406.
   */
  @ParameterizedTest
  @ValueSource(strings = {
      "application/json",
      "application/vnd.github+json",
      "application/vnd.github.v3+json",
      "application/vnd.github.machine-man-preview+json",
      "*/*"
  })
  void shouldServeCurrentUserForAnyJsonAccept(String accept) {
    Mockito.when(giteaClient.userGetCurrent())
        .thenReturn(ResponseEntity.ok(new User(
            null, null, null, null, "The Octocat", null, 5L, null, null, "octocat"
        )));

    client.get()
        .uri("/api/v3/user")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .header(HttpHeaders.ACCEPT, accept)
        .exchange()
        .expectStatus().isOk()
        .expectBody().jsonPath("$.login").isEqualTo("octocat");
  }
}
