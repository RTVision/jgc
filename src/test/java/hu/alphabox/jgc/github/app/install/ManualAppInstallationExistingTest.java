package hu.alphabox.jgc.github.app.install;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Exercises the manual app installation flow against the default test context, where
 * src/test/resources/test-installation.json is already loaded as the app installation. Neither the
 * GET form page nor the POST creation endpoint mutate any state when an installation is already
 * present, so it is safe to share the common cached Spring context (and fixture file) used by
 * other test classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ManualAppInstallationExistingTest {

  @Autowired
  private MockMvcTester mvc;

  @Test
  @WithMockUser
  void showManualAppFormShouldRedirectToErrorPageWhenAlreadyInstalled() {
    mvc.get()
        .uri("/settings/apps/new")
        .assertThat()
        .hasStatus3xxRedirection()
        .redirectedUrl()
        .contains("/application-error");
  }

  @Test
  @WithMockUser
  void createManualAppInstallationShouldRedirectToErrorPageWhenAlreadyInstalled() {
    mvc.post()
        .uri("/settings/apps/manual")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .assertThat()
        .hasStatus3xxRedirection()
        .redirectedUrl()
        .contains("/application-error");
  }

  @Test
  void showManualAppFormShouldRedirectToTokenLoginWhenUnauthenticated() {
    mvc.get()
        .uri("/settings/apps/new")
        .assertThat()
        .hasStatus3xxRedirection()
        .redirectedUrl()
        .startsWith("/login/token");
  }

}
