package hu.alphabox.jgc.github;

import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class GitHubOAuthAccessTokenTest {

  @Autowired
  private RestTestClient client;

  @Autowired
  private GitHubAppInstallationManager installationManager;

  @Autowired
  private GitHubUserAccessTokenService tokenService;

  @MockitoBean
  private GiteaClient giteaClient;

  @Test
  void shouldExchangeCodeSentAsJson() {
    GitHubAppInstallation appInstallation = installationManager.getAppInstallation().orElseThrow();

    client.post()
        .uri("/login/oauth/access_token")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of(
            "client_id", appInstallation.getClientId(),
            "client_secret", appInstallation.getClientSecret(),
            "code", tokenService.generateTemporaryCode()
        ))
        .exchange()
        .expectStatus().isOk()
        .expectBody().jsonPath("$.access_token").isNotEmpty();
  }

  /**
   * Sentry's GitHub Enterprise integration exchanges the code with a form-encoded body, which
   * GitHub accepts alongside JSON.
   */
  @Test
  void shouldExchangeCodeSentAsForm() {
    GitHubAppInstallation appInstallation = installationManager.getAppInstallation().orElseThrow();

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", appInstallation.getClientId());
    form.add("client_secret", appInstallation.getClientSecret());
    form.add("code", tokenService.generateTemporaryCode());
    form.add("grant_type", "authorization_code");
    form.add("redirect_uri", "https://sentry.io/extensions/github-enterprise/setup/");

    client.post()
        .uri("/login/oauth/access_token")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .exchange()
        .expectStatus().isOk()
        .expectBody().jsonPath("$.access_token").isNotEmpty();
  }
}
