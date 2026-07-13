package hu.alphabox.jgc.github.app.install;

import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.User;
import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import java.io.File;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Exercises the manual app installation flow in isolation from the shared test-installation.json
 * fixture used by other tests: application.installation-path is overridden to a scratch file under
 * target/, which is deleted before the Spring context (and therefore GitHubAppInstallationManager)
 * initializes, so this class starts out with no app installation configured.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = "application.installation-path=target/manual-app-installation-test.json")
class ManualAppInstallationCreationTest {

  static {
    new File("target/manual-app-installation-test.json").delete();
    new File("target/manual-app-installation-test.json.tokens.json").delete();
  }

  @Autowired
  private MockMvcTester mvc;

  @Autowired
  private GitHubAppInstallationManager installationManager;

  @MockitoBean
  private GiteaClient giteaClient;

  @Test
  @Order(1)
  @WithMockUser
  void showManualAppFormShouldRenderFormWhenNoInstallationExists() {
    Assertions.assertThat(installationManager.getAppInstallation()).isEmpty();

    mvc.get()
        .uri("/settings/apps/new")
        .assertThat()
        .hasStatusOk()
        .bodyText()
        .contains("action=\"/settings/apps/manual\"")
        .contains("name=\"webhookUrl\"");
  }

  @Test
  @Order(2)
  @WithMockUser
  void createManualAppInstallationShouldCreateInstallationAndDisplayCredentialsOnce() {
    Mockito.when(giteaClient.userGetCurrent())
        .thenReturn(ResponseEntity.ok(new User(null, null, null, null, null, null, 41L, null, null, "octocat")));

    var result = mvc.post()
        .uri("/settings/apps/manual")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("webhookUrl", "https://sentry.io/extensions/github-enterprise/webhook/")
        .param("callbackUrl", "https://sentry.io/extensions/github-enterprise/setup/")
        .param("setupUrl", "https://sentry.io/extensions/github-enterprise/setup/")
        .exchange();

    var installation = installationManager.getAppInstallation();
    Assertions.assertThat(installation).isPresent();
    Assertions.assertThat(installation.get().getOwnerId()).isEqualTo(41L);
    Assertions.assertThat(installation.get().getWebhookUrl()).isEqualTo("https://sentry.io/extensions/github-enterprise/webhook/");
    Assertions.assertThat(installation.get().getDefaultEvents()).isEqualTo(List.of());
    Assertions.assertThat(installation.get().getDefaultPermissions()).isEmpty();
    Assertions.assertThat(installation.get().getInstallations()).isEmpty();

    GitHubAppInstallation created = installation.get();

    result.assertThat()
        .hasStatusOk()
        .bodyText()
        .contains(created.getClientId())
        .contains(created.getClientSecret())
        .contains("BEGIN RSA PRIVATE KEY");
  }

  @Test
  @Order(3)
  @WithMockUser
  void createManualAppInstallationShouldRedirectToErrorPageWhenAlreadyInstalled() {
    Assertions.assertThat(installationManager.getAppInstallation()).isPresent();

    mvc.post()
        .uri("/settings/apps/manual")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .assertThat()
        .hasStatus3xxRedirection()
        .redirectedUrl()
        .contains("/application-error");
  }

}
