package hu.alphabox.jgc.github.app.install;

import hu.alphabox.jgc.ApplicationProperties;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.CreateHookOption;
import hu.alphabox.jgc.gitea.client.model.CreateHookOption.TypeEnum;
import hu.alphabox.jgc.gitea.client.model.Organization;
import hu.alphabox.jgc.gitea.client.model.User;
import hu.alphabox.jgc.gitea.webhook.GiteaWebhookHandler;
import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallation.Installation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import hu.alphabox.jgc.github.app.install.UserInstallationsResponse.Installation.Account;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@EnableConfigurationProperties(ApplicationProperties.class)
@Slf4j
class InstallationHandler {

  // the installation time is not persisted, so report a fixed timestamp; consumers
  // (e.g. Drata) only need parseable dates, not accurate ones
  private static final Instant INSTALLATION_TIMESTAMP = Instant.parse("2024-01-01T00:00:00Z");

  private final InstallationService service;
  private final GiteaClient giteaClient;
  private final GitHubAppInstallationManager installationManager;
  private final ApplicationProperties properties;

  @PostMapping("/installations/{installation-id}/organizations/select")
  ResponseEntity<OrganizationSelectResponse> selectOrganization(
      @PathVariable("installation-id") Long installationId,
      @RequestParam(value = "state", required = false) String state,
      @RequestBody @Valid OrganizationSelectRequest request
  ) {
    Optional<GitHubAppInstallation> optionalAppInstallation = installationManager.getAppInstallation();
    if (optionalAppInstallation.isEmpty()) {
      log.error("App installation not found");
      return ResponseEntity.notFound().build();
    }
    GitHubAppInstallation appInstallation = optionalAppInstallation.get();

    Optional<Installation> optionalInstallation = appInstallation.getInstallation(installationId);
    if (optionalInstallation.isEmpty()) {
      log.error("Not found installation with id {}", installationId);
      return ResponseEntity.notFound().build();
    }

    Installation installation = optionalInstallation.get();
    UriComponentsBuilder repositoriesUri = UriComponentsBuilder
        .fromPath("/jira-gitea-connector/installations/%d/repositories".formatted(installation.getId()));
    if (state != null) {
      repositoriesUri.queryParam("state", state);
    }
    OrganizationSelectResponse successResponse = new OrganizationSelectResponse(repositoriesUri.toUriString());

    if (installation.isConfigured()) {
      log.warn("Installation with id: {} already configured for organization: {}", installation.getId(), installation.getOrganizationId());
      return ResponseEntity.ok(successResponse);
    }

    Optional<Organization> optionalOrganization = service.findOrganizationForUser(request.organizationId());
    if (optionalOrganization.isEmpty()) {
      log.error("Given organization id ({}) not found in user's visible organizations", request.organizationId());
      return ResponseEntity.notFound().build();
    }

    Organization organization = optionalOrganization.get();
    if (appInstallation.getWebhookUrl() != null) {
      giteaClient.orgCreateHook(organization.name(), createHookRequest(installationId, appInstallation.getWebhookSecret()));
    } else {
      log.info("App was registered without webhook attributes, skipping Gitea hook creation for organization: {}", organization.name());
    }

    installation.setOrganizationId(organization.id());
    installation.setSelectedRepositories(new LinkedHashSet<>());
    installationManager.save();

    log.info("New installation configured with organization ID: {}", installation.getOrganizationId());
    return ResponseEntity.ok(successResponse);
  }

  @PostMapping("/installations/{installation-id}/repositories/select")
  ResponseEntity<OrganizationSelectResponse> selectRepositories(
      @PathVariable("installation-id") Long installationId,
      @RequestParam(value = "state", required = false) String state,
      @RequestBody RepositorySelectRequest request
  ) {
    Optional<GitHubAppInstallation> optionalAppInstallation = installationManager.getAppInstallation();
    if (optionalAppInstallation.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    GitHubAppInstallation appInstallation = optionalAppInstallation.get();
    Optional<Installation> optionalInstallation = appInstallation.getInstallation(installationId);
    if (optionalInstallation.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    Installation installation = optionalInstallation.get();
    installation.setSelectedRepositories(request.repositories() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(request.repositories()));
    installationManager.save();

    return ResponseEntity.ok(new OrganizationSelectResponse(
        UriComponentsBuilder
            .fromUriString(appInstallation.getSetupUrl())
            // the Jira plugin validates the state it opened the flow with (e.g. "spa")
            .queryParam("state", state != null ? state : "non-spa")
            .queryParam("installation_id", installation.getId())
            // GitHub always includes setup_action when redirecting to the app's setup URL
            .queryParam("setup_action", "install")
            .toUriString()
    ));
  }

  private CreateHookOption createHookRequest(Long installationId, String webhookSecret) {
    return new CreateHookOption(
        true,
        Map.of(
            "secret", webhookSecret,
            "content_type", "json",
            "url", "%s%s/%d".formatted(properties.baseUrl(), GiteaWebhookHandler.API_MAPPING, installationId)
        ),
        List.of(
            "create",
            "delete",
            "push",
            "pull_request_only",
            "pull_request_comment",
            "pull_request_review",
            "pull_request_review_request",
            "repository"
        ),
        TypeEnum.GITEA
    );
  }


  @GetMapping("/api/v3/app/installations/{installation-id}")
  ResponseEntity<InstallationResponse> handleInstallation(@PathVariable("installation-id") long id) {
    Optional<GitHubAppInstallation> optionalAppInstallation = installationManager.getAppInstallation();
    if (optionalAppInstallation.isEmpty()) {
      log.error("App installation not found");
      return ResponseEntity.notFound().build();
    }

    GitHubAppInstallation appInstallation = optionalAppInstallation.get();
    Optional<Installation> optionalInstallation = appInstallation.getInstallation(id);

    if (optionalInstallation.isEmpty()) {
      log.error("Not found installation with id {}", id);
      return ResponseEntity.notFound().build();
    }
    Installation installation = optionalInstallation.get();

    Optional<Organization> organizationOptional = service.findOrganizationForUser(installation.getOrganizationId());

    if (organizationOptional.isEmpty()) {
      log.error("Organization with id: {} not found in user's visible organizations", installation.getOrganizationId());
      return ResponseEntity.notFound().build();
    }

    Organization organization = organizationOptional.get();

    return ResponseEntity.ok(
        new InstallationResponse(
            installation.getId(),
            appInstallation.getClientId(),
            GitHubAccountResponse.of(organization.id(), organization.name(), organization.avatarUrl(), ActorType.ORGANIZATION, properties.baseUrl()),
            installation.hasSelectedRepositories() ? RepositorySelection.SELECTED : RepositorySelection.ALL,
            "%s/api/v3/app/installations/%d/access_tokens".formatted(
                properties.baseUrl(),
                installation.getId()
            ),
            "%s/api/v3/installation/repositories".formatted(properties.baseUrl()),
            "%s/organizations/%s/settings/installations/%d".formatted(
                properties.baseUrl(),
                organization.name(),
                installation.getId()
            ),
            appInstallation.getId(),
            AppInstallationHandler.APPLICATION_SLUG,
            organization.id(),
            ActorType.ORGANIZATION,
            appInstallation.getDefaultPermissions(),
            appInstallation.getDefaultEvents(),
            INSTALLATION_TIMESTAMP,
            INSTALLATION_TIMESTAMP,
            null,
            false,
            List.of(),
            null,
            null
        )
    );
  }

  @GetMapping(value = "/api/v3/user", produces = "application/vnd.github.v3+json")
  ResponseEntity<UserResponse> handleUser() {
    ResponseEntity<User> response = giteaClient.userGetCurrent();
    User giteaUser = response.getBody();

    return ResponseEntity.ok(new UserResponse(
        giteaUser.login(),
        giteaUser.id(),
        giteaUser.avatarUrl(),
        ActorType.USER,
        giteaUser.fullName()
    ));
  }

  @GetMapping(value = "/api/v3/user/installations", produces = "application/vnd.github.v3+json")
  ResponseEntity<UserInstallationsResponse> handleInstallations() {
    Optional<GitHubAppInstallation> optionalAppInstallation = installationManager.getAppInstallation();

    if (optionalAppInstallation.isEmpty()) {
      return ResponseEntity.ok(new UserInstallationsResponse(
          0,
          Collections.emptyList()
      ));
    }

    GitHubAppInstallation appInstallation = optionalAppInstallation.get();

    return ResponseEntity.ok(
        new UserInstallationsResponse(
            appInstallation.getInstallations().size(),
            appInstallation.getInstallations().stream().map(installation -> {
              Optional<Organization> optionalOrganization = service.findOrganizationForUser(installation.getOrganizationId());
              if (optionalOrganization.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization with id: " + installation.getOrganizationId() + " not found");
              }
              Organization organization = optionalOrganization.get();

              return new UserInstallationsResponse.Installation(
                  installation.getId(),
                  new Account(
                      organization.id(),
                      organization.name(),
                      ActorType.ORGANIZATION
                  ),
                  appInstallation.getId(),
                  installation.getOrganizationId(),
                  ActorType.ORGANIZATION,
                  appInstallation.getDefaultPermissions(),
                  appInstallation.getDefaultEvents(),
                  installation.hasSelectedRepositories() ? RepositorySelection.SELECTED : RepositorySelection.ALL
              );
            }).toList()
        )
    );
  }

  @GetMapping(value = "/api/v3/orgs/{org}/memberships/{user}")
  ResponseEntity<MembershipResponse> handleMemberships(
      @PathVariable String org,
      @PathVariable String user
  ) {

    Organization organization;
    User giteaUser;
    try {
      ResponseEntity<Organization> organizationResponse = giteaClient.orgGet(org);
      organization = organizationResponse.getBody();
    } catch (HttpClientErrorException.NotFound _) {
      log.error("Not found organization with name {}", org);
      return ResponseEntity.notFound().build();
    }

    try {
      ResponseEntity<User> userResponse = giteaClient.userGet(user);
      giteaUser = userResponse.getBody();
    } catch (HttpClientErrorException.NotFound _) {
      log.error("Not found user with name {}", user);
      return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok(
        new MembershipResponse(
            "active",
            "admin",
            new MembershipResponse.User(
                giteaUser.login(),
                giteaUser.id(),
                ActorType.USER
            ),
            new MembershipResponse.Organization(
                organization.name(),
                organization.id()
            )
        )
    );
  }
}
