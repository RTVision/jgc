package hu.alphabox.jgc.github.app.install;

import com.fasterxml.jackson.annotation.JsonFormat;
import hu.alphabox.jgc.ApplicationProperties;
import hu.alphabox.jgc.ErrorPageController;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.User;
import hu.alphabox.jgc.github.GitHubServerAccessTokenService;
import hu.alphabox.jgc.github.GitHubServerAccessTokenService.OpaqueToken;
import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import hu.alphabox.jgc.github.app.GitHubAppKeyHandler;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@RestController
@RequiredArgsConstructor
@EnableConfigurationProperties(ApplicationProperties.class)
class AppInstallationHandler {

  static final Long APPLICATION_DEFAULT_ID = 1L;
  private static final String APPLICATION_NAME = "Gitea";
  // slugified APPLICATION_NAME; also reported as app_slug by the installation endpoint
  static final String APPLICATION_SLUG = "gitea";
  // app registration time is not persisted, so report a fixed parseable timestamp
  private static final Instant REGISTERED_TIMESTAMP = Instant.parse("2024-01-01T00:00:00Z");

  private final InstallationService service;
  private final GiteaClient giteaClient;
  private final ApplicationProperties applicationProperties;
  private final GitHubAppInstallationManager installationManager;
  private final JsonMapper jsonMapper;
  private final GitHubServerAccessTokenService tokenService;

  @PostMapping(path = "/settings/apps/new", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  ResponseEntity<Void> handle(@RequestParam String manifest, @RequestParam(required = false) String state) {

    log.debug("New app installation request with state: {}, manifest: {}", state, manifest);
    if (installationManager.getAppInstallation().isPresent()) {
      log.warn("Failed to create a new app installation because one is already configured.");
      return ResponseEntity.status(HttpStatus.FOUND)
          .header(HttpHeaders.LOCATION, ErrorPageController.generateRedirectUrl(
              "Application already installed",
              "The connector app has already been configured once. If you would like to configure it again, please delete the installation.json file first, then restart the connector."
          ))
          .build();
    }

    Long giteaUserId;
    try {
      ResponseEntity<User> response = giteaClient.userGetCurrent();
      giteaUserId = response.getBody().id();
    } catch (HttpClientErrorException e) {
      log.error("Error while getting current gitea user.", e);
      return ResponseEntity.status(HttpStatus.FOUND)
          .header(HttpHeaders.LOCATION, ErrorPageController.generateRedirectUrl(
              "Configuration error",
              "Unable to retrieve the logged-in Gitea user. Please check the accessibility of the Gitea app and verify your token."
          ))
          .build();
    }

    Manifest parsedManifest = jsonMapper.readValue(manifest, Manifest.class);
    // hook_attributes is optional in the manifest; webhook-less integrations (e.g. Drata) omit it
    Manifest.HookAttributes hookAttributes = parsedManifest.hookAttributes();

    KeyPair keyPair = GitHubAppKeyHandler.generate();
    installationManager.getInstallationRegistrar().mark(
        new GitHubAppInstallation(
            APPLICATION_DEFAULT_ID,
            service.generateClientId(),
            service.generateSecret(),
            hookAttributes != null ? hookAttributes.url() : null,
            parsedManifest.callbackUrl(),
            parsedManifest.setupUrl(),
            GitHubAppKeyHandler.asPEMFormattedPKCS8((RSAPrivateKey) keyPair.getPrivate()),
            GitHubAppKeyHandler.asX509SubjectPublicKeyInfo((RSAPublicKey) keyPair.getPublic()),
            parsedManifest.defaultEvents() != null ? parsedManifest.defaultEvents() : List.of(),
            parsedManifest.defaultPermissions() != null ? parsedManifest.defaultPermissions() : Map.of(),
            new HashSet<>(),
            giteaUserId
        )
    );

    // GitHub echoes the state parameter back on the manifest redirect; integrations (e.g. Drata) validate it
    UriComponentsBuilder redirectBuilder = UriComponentsBuilder
        .fromUriString(parsedManifest.redirectUrl())
        .queryParam("code", APPLICATION_DEFAULT_ID);
    if (state != null) {
      redirectBuilder.queryParam("state", state);
    }

    return ResponseEntity.status(HttpStatus.FOUND)
        .header("Location", redirectBuilder.build().toUriString())
        .build();
  }

  @PostMapping("/api/v3/app-manifests/{code}/conversions")
  ResponseEntity<AppConversionsResponse> handleAppManifestConversions(@PathVariable String code) {
    Optional<GitHubAppInstallation> optionalInstallation = installationManager.getInstallationRegistrar().getInstallation();
    if (optionalInstallation.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    String webhookSecret = service.generateSecret();
    GitHubAppInstallation installation = optionalInstallation.get();
    installation.setWebhookSecret(webhookSecret);
    installationManager.setAppInstallation(installation);
    log.debug("New app installation created: {}", installation);

    User giteaUser = giteaClient.userGetCurrent().getBody();

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new AppConversionsResponse(
            installation.getId(),
            APPLICATION_SLUG,
            Base64.getEncoder().encodeToString("03:App%d".formatted(installation.getId()).getBytes(StandardCharsets.UTF_8)),
            GitHubAccountResponse.of(giteaUser.id(), giteaUser.login(), giteaUser.avatarUrl(), ActorType.USER, applicationProperties.baseUrl()),
            APPLICATION_NAME,
            null,
            applicationProperties.baseUrl(),
            "%s/apps/%s".formatted(applicationProperties.baseUrl(), APPLICATION_SLUG),
            REGISTERED_TIMESTAMP,
            REGISTERED_TIMESTAMP,
            installation.getDefaultPermissions(),
            installation.getDefaultEvents(),
            installation.getClientId(),
            installation.getClientSecret(),
            webhookSecret,
            GitHubAppKeyHandler.asPEMFormattedPKCS1(GitHubAppKeyHandler.fromPemFormattedPKCS8(installation.getPrivateKey()))
        ));
  }

  @GetMapping("/api/v3/app")
  ResponseEntity<AppResponse> handleApp() {
    Optional<GitHubAppInstallation> optionalInstallation = installationManager.getAppInstallation();
    if (optionalInstallation.isEmpty()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    GitHubAppInstallation appInstallation = optionalInstallation.get();

    User giteaUser = giteaClient.userGetCurrent().getBody();
    if (!giteaUser.id().equals(appInstallation.getOwnerId())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    return ResponseEntity.ok()
        .body(new AppResponse(
            appInstallation.getId(),
            appInstallation.getClientId(),
            APPLICATION_NAME,
            "%s/apps/jira-gitea-connector".formatted(applicationProperties.baseUrl()),
            "%s/jira-gitea-connector".formatted(applicationProperties.baseUrl()),
            appInstallation.getInstallations().size(),
            new AppResponse.Owner(
                giteaUser.id(),
                giteaUser.login(),
                ActorType.USER
            ),
            appInstallation.getDefaultPermissions(),
            appInstallation.getDefaultEvents()
        ));
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  record AccessTokenResponse(
      String token,
      @JsonFormat(shape = JsonFormat.Shape.STRING)
      Instant expiresAt,
      Map<String, String> permissions,
      RepositorySelection repositorySelection
  ) {

  }

  @PostMapping("/api/v3/app/installations/{installation-id}/access_tokens")
  ResponseEntity<AccessTokenResponse> handleAppInstallationAccessTokens(
      Authentication authentication,
      @PathVariable("installation-id") Long installationId
  ) {
    OpaqueToken opaqueToken = tokenService.generateToken(authentication, installationId);
    Optional<GitHubAppInstallation> optionalAppInstallation = installationManager.getAppInstallation();
    Map<String, String> permissions = optionalAppInstallation
        .map(GitHubAppInstallation::getDefaultPermissions)
        .orElseGet(Map::of);
    RepositorySelection repositorySelection = optionalAppInstallation
        .flatMap(appInstallation -> appInstallation.getInstallation(installationId))
        .map(installation -> installation.hasSelectedRepositories() ? RepositorySelection.SELECTED : RepositorySelection.ALL)
        .orElse(RepositorySelection.ALL);
    return ResponseEntity.status(HttpStatus.CREATED)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new AccessTokenResponse(
            opaqueToken.token(),
            opaqueToken.expiresAt(),
            permissions,
            repositorySelection
        ));
  }

}
