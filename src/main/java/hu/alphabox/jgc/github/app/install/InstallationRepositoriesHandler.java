package hu.alphabox.jgc.github.app.install;

import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.Organization;
import hu.alphabox.jgc.gitea.client.model.Repository;
import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallation.Installation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import hu.alphabox.jgc.github.rest.repository.GitHubRepositoryMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3")
@RequiredArgsConstructor
class InstallationRepositoriesHandler {

  private final GitHubAppInstallationManager installationManager;
  private final InstallationService installationService;
  private final GiteaClient giteaClient;
  private final GitHubRepositoryMapper repositoryMapper;
  private final AllowedRepositoryService allowedRepositoryService;

  @GetMapping("/user/installations/{installation-id}/repositories")
  ResponseEntity<InstallationRepositoriesResponse> userInstallationRepositories(@PathVariable("installation-id") Long installationId) {
    return installationRepositories(installationId);
  }

  @GetMapping("/installation/repositories")
  ResponseEntity<InstallationRepositoriesResponse> installationRepositories() {
    Optional<GitHubAppInstallation> optionalAppInstallation = installationManager.getAppInstallation();
    if (optionalAppInstallation.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    Optional<Installation> optionalInstallation = optionalAppInstallation.get().getInstallations().stream().findFirst();
    if (optionalInstallation.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    return installationRepositories(optionalInstallation.get().getId());
  }

  private ResponseEntity<InstallationRepositoriesResponse> installationRepositories(Long installationId) {
    Optional<GitHubAppInstallation> optionalAppInstallation = installationManager.getAppInstallation();
    if (optionalAppInstallation.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    Optional<Installation> optionalInstallation = optionalAppInstallation.get().getInstallation(installationId);
    if (optionalInstallation.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    Installation installation = optionalInstallation.get();
    Optional<Organization> optionalOrganization = installationService.findOrganizationForUser(installation.getOrganizationId());
    if (optionalOrganization.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    List<Repository> repositories = giteaClient.orgListRepos(optionalOrganization.get().name(), 1, 100).getBody();
    if (repositories == null) {
      repositories = List.of();
    }

    List<Repository> selectedRepositories = repositories.stream()
        .filter(repository -> allowedRepositoryService.isAllowed(repository.fullName()))
        .filter(repository -> !installation.hasSelectedRepositories() || installation.getSelectedRepositories().contains(repository.fullName()))
        .toList();

    return ResponseEntity.ok(new InstallationRepositoriesResponse(
        selectedRepositories.size(),
        installation.hasSelectedRepositories() ? RepositorySelection.SELECTED : RepositorySelection.ALL,
        selectedRepositories.stream().map(repositoryMapper::repository).toList()
    ));
  }
}
