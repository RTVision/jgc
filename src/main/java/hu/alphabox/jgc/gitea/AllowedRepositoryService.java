package hu.alphabox.jgc.gitea;

import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AllowedRepositoryService {

  private final Set<String> configuredAllowedRepositories;
  private final GitHubAppInstallationManager installationManager;

  public AllowedRepositoryService(GiteaConfigurationProperties properties, GitHubAppInstallationManager installationManager) {
    this.configuredAllowedRepositories = Set.copyOf(properties.allowedRepositories());
    this.installationManager = installationManager;
  }

  public boolean isAllowed(String owner, String repository) {
    return isAllowed("%s/%s".formatted(owner, repository));
  }

  public boolean isAllowed(String repositoryFullName) {
    Set<String> effectiveAllowedRepositories = effectiveAllowedRepositories();
    return effectiveAllowedRepositories.isEmpty() || effectiveAllowedRepositories.contains(repositoryFullName);
  }

  public Set<String> effectiveAllowedRepositories() {
    Set<String> configuredSelection = configuredAllowedRepositories;
    Set<String> installationSelection = installationManager.getAppInstallation()
        .map(GitHubAppInstallation::getInstallations)
        .stream()
        .flatMap(Set::stream)
        .filter(GitHubAppInstallation.Installation::hasSelectedRepositories)
        .flatMap(installation -> installation.getSelectedRepositories().stream())
        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

    if (configuredSelection.isEmpty()) {
      return installationSelection;
    }
    if (installationSelection.isEmpty()) {
      return configuredSelection;
    }

    return configuredSelection.stream()
        .filter(installationSelection::contains)
        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  public Set<String> configuredAllowedRepositories() {
    return configuredAllowedRepositories;
  }
}
