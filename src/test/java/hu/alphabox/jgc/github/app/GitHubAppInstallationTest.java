package hu.alphabox.jgc.github.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GitHubAppInstallationTest {

  @Test
  void slugIsDerivedFromManifestName() {
    assertThat(installation("Drata").getSlug()).isEqualTo("drata");
    assertThat(installation("My Cool App!").getSlug()).isEqualTo("my-cool-app");
  }

  /**
   * Installations registered before the manifest name was persisted keep reporting the original app identity.
   */
  @Test
  void installationWithoutNameFallsBackToGitea() {
    GitHubAppInstallation installation = installation(null);

    assertThat(installation.getName()).isEqualTo("Gitea");
    assertThat(installation.getSlug()).isEqualTo("gitea");
  }

  private static GitHubAppInstallation installation(String name) {
    return new GitHubAppInstallation(1L, "client", "secret", null, null, null, "private", "public",
        List.of(), Map.of(), new HashSet<>(), 1L, name);
  }
}
