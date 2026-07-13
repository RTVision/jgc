package hu.alphabox.jgc.github.app.install;

import hu.alphabox.jgc.ErrorPageController;
import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.Organization;
import hu.alphabox.jgc.gitea.client.model.Repository;
import hu.alphabox.jgc.gitea.client.model.User;
import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallation.Installation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import hu.alphabox.jgc.github.app.GitHubAppKeyHandler;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Controller
@RequiredArgsConstructor
class InstallationPageController {

  private static final String APPLICATION_SLUG = "jira-gitea-connector";

  private final InstallationService service;
  private final GitHubAppInstallationManager installationManager;
  private final AllowedRepositoryService allowedRepositoryService;
  private final GiteaClient giteaClient;

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

  // Manual app creation flow, mirroring the GitHub App manifest flow for integrations (e.g. Sentry)
  // that require manually creating the app and pasting credentials in rather than using the manifest flow.
  @GetMapping("/settings/apps/new")
  String showManualAppForm() {
    if (installationManager.getAppInstallation().isPresent()) {
      return "redirect: " + ErrorPageController.generateRedirectUrl(
          "Application already installed",
          "The connector app has already been configured once. If you would like to configure it again, please delete the installation.json file first, then restart the connector."
      );
    }

    return "manual-app-form";
  }

  @PostMapping(path = "/settings/apps/manual", consumes = "application/x-www-form-urlencoded")
  String createManualAppInstallation(
      @RequestParam(required = false) String webhookUrl,
      @RequestParam(required = false) String webhookSecret,
      @RequestParam(required = false) String callbackUrl,
      @RequestParam(required = false) String setupUrl,
      Model model
  ) {
    if (installationManager.getAppInstallation().isPresent()) {
      return "redirect: " + ErrorPageController.generateRedirectUrl(
          "Application already installed",
          "The connector app has already been configured once. If you would like to configure it again, please delete the installation.json file first, then restart the connector."
      );
    }

    Long giteaUserId;
    try {
      ResponseEntity<User> response = giteaClient.userGetCurrent();
      giteaUserId = response.getBody().id();
    } catch (HttpClientErrorException e) {
      return "redirect: " + ErrorPageController.generateRedirectUrl(
          "Configuration error",
          "Unable to retrieve the logged-in Gitea user. Please check the accessibility of the Gitea app and verify your token."
      );
    }

    KeyPair keyPair = GitHubAppKeyHandler.generate();
    String resolvedWebhookSecret = StringUtils.hasText(webhookSecret) ? webhookSecret : service.generateSecret();

    GitHubAppInstallation installation = new GitHubAppInstallation(
        AppInstallationHandler.APPLICATION_DEFAULT_ID,
        service.generateClientId(),
        service.generateSecret(),
        StringUtils.hasText(webhookUrl) ? webhookUrl : null,
        StringUtils.hasText(callbackUrl) ? callbackUrl : null,
        StringUtils.hasText(setupUrl) ? setupUrl : null,
        GitHubAppKeyHandler.asPEMFormattedPKCS8((RSAPrivateKey) keyPair.getPrivate()),
        GitHubAppKeyHandler.asX509SubjectPublicKeyInfo((RSAPublicKey) keyPair.getPublic()),
        List.of(),
        Map.of(),
        new HashSet<>(),
        giteaUserId
    );
    installation.setWebhookSecret(resolvedWebhookSecret);
    installationManager.setAppInstallation(installation);
    log.debug("New manually configured app installation created: {}", installation);

    model.addAttribute("appId", installation.getId());
    model.addAttribute("appSlug", APPLICATION_SLUG);
    model.addAttribute("clientId", installation.getClientId());
    model.addAttribute("clientSecret", installation.getClientSecret());
    model.addAttribute("webhookSecret", resolvedWebhookSecret);
    model.addAttribute("privateKeyPkcs1",
        GitHubAppKeyHandler.asPEMFormattedPKCS1(GitHubAppKeyHandler.fromPemFormattedPKCS8(installation.getPrivateKey())));

    return "manual-app-credentials";
  }
}
