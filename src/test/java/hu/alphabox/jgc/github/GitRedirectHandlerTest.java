package hu.alphabox.jgc.github;

import hu.alphabox.jgc.gitea.client.GiteaClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class GitRedirectHandlerTest {

  @Autowired
  private RestTestClient client;

  @MockitoBean
  private GiteaClient giteaClient;

  /**
   * Sentry links stack frames and commits to its GitHub base URL, which is this connector, so
   * the links have to land on the matching Gitea page without a login.
   */
  @ParameterizedTest
  @CsvSource({
      "/TestOrg/TestRepo/tree/main, /TestOrg/TestRepo/tree/main",
      "/TestOrg/TestRepo/commit/abc123, /TestOrg/TestRepo/commit/abc123",
      "/TestOrg/TestRepo/compare/abc123...def456, /TestOrg/TestRepo/compare/abc123...def456",
      "/TestOrg/TestRepo/blob/main/apps/api/src/index.ts, /TestOrg/TestRepo/src/main/apps/api/src/index.ts",
      "/TestOrg/TestRepo/pull/42, /TestOrg/TestRepo/pulls/42"
  })
  void shouldRedirectGitHubBrowserLinksToGitea(String path, String giteaPath) {
    client.get()
        .uri(path)
        .exchange()
        .expectStatus().isFound()
        .expectHeader().value("Location", location -> Assertions.assertThat(location).endsWith(giteaPath));
  }
}
