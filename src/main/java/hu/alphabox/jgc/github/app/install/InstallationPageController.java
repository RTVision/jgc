package hu.alphabox.jgc.github.app.install;

import hu.alphabox.jgc.ErrorPageController;
import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.model.Organization;
import hu.alphabox.jgc.gitea.client.model.Repository;
import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallation.Installation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
class InstallationPageController {

  private final InstallationService service;
  private final GitHubAppInstallationManager installationManager;
  private final AllowedRepositoryService allowedRepositoryService;

  @GetMapping("/jira-gitea-connector/installations/new")
  String showOrganizationSelector(Model model) {

    Optional<GitHubAppInstallation> optionalInstallation = installationManager.getAppInstallation();
    if (optionalInstallation.isEmpty()) {
      return "redirect: " + ErrorPageController.generateRedirectUrl(
          "Unexpected error",
          "An unexpected error has occurred while processing your request"
      );
    }

    record OrganizationModel(
        long id,
        String name,
        String avatarUrl
    ) {

    }

    List<OrganizationModel> organizations = service.getOrganizationsForUser()
        .stream()
        .map(organization -> new OrganizationModel(
            organization.id(),
            organization.name(),
            organization.avatarUrl()
        ))
        .toList();

    GitHubAppInstallation appInstallation = optionalInstallation.get();
    Installation installation = appInstallation.resolveInstallationForConfiguration();
    appInstallation.addInstallation(installation);

    model.addAttribute("organizations", organizations);
    model.addAttribute("installationId", installation.getId());

    return "gitea-installation-chooser";
  }

  // GitHub's app settings installations page; integrations (e.g. Drata) link here to install the app
  @GetMapping("/settings/apps/{app-slug}/installations")
  String redirectToOrganizationSelector() {
    return "redirect:/jira-gitea-connector/installations/new";
  }

  @GetMapping("/settings/installations/{installation-id}")
  String redirectToRepositorySelector(@PathVariable("installation-id") Long installationId) {
    return "redirect:/jira-gitea-connector/installations/%d/repositories".formatted(installationId);
  }

  @GetMapping("/jira-gitea-connector/installations/{installation-id}/repositories")
  String showRepositorySelector(@PathVariable("installation-id") Long installationId, Model model) {
    Optional<GitHubAppInstallation> optionalInstallation = installationManager.getAppInstallation();
    if (optionalInstallation.isEmpty()) {
      return "redirect: " + ErrorPageController.generateRedirectUrl(
          "Unexpected error",
          "An unexpected error has occurred while processing your request"
      );
    }

    GitHubAppInstallation appInstallation = optionalInstallation.get();
    Optional<Installation> optionalConfiguredInstallation = appInstallation.getInstallation(installationId);
    if (optionalConfiguredInstallation.isEmpty()) {
      return "redirect: " + ErrorPageController.generateRedirectUrl(
          "Unexpected error",
          "Installation was not found"
      );
    }

    Installation installation = optionalConfiguredInstallation.get();
    if (!installation.isConfigured()) {
      return "redirect:/jira-gitea-connector/installations/new";
    }

    Optional<Organization> optionalOrganization = service.findOrganizationForUser(installation.getOrganizationId());
    if (optionalOrganization.isEmpty()) {
      return "redirect: " + ErrorPageController.generateRedirectUrl(
          "Unexpected error",
          "Organization was not found"
      );
    }

    Organization organization = optionalOrganization.get();
    Set<String> configuredAllowedRepositories = allowedRepositoryService.configuredAllowedRepositories();

    List<Repository> repositories = service.listAllOrganizationRepositories(organization.name());

    List<Repository> visibleRepositories = repositories.stream()
        .filter(repository -> configuredAllowedRepositories.isEmpty() || configuredAllowedRepositories.contains(repository.fullName()))
        .toList();

    model.addAttribute("repositories", visibleRepositories);
    model.addAttribute("selectedRepositories", installation.getSelectedRepositories());
    model.addAttribute("organizationName", organization.name());
    model.addAttribute("installationId", installationId);
    return "gitea-installation-chooser";
  }
}
