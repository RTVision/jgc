package hu.alphabox.jgc.github;

import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import hu.alphabox.jgc.github.app.GitHubAppInstallationMissingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Slf4j
@RestController
@RequiredArgsConstructor
class GitHubOAuthCallHandler {

  private final GitHubUserAccessTokenService tokenService;
  private final GitHubAppInstallationManager installationManager;

  @GetMapping("/login/oauth/authorize")
  ResponseEntity<Void> handleOAuth2Authorize(@RequestParam(name = "redirect_uri") String redirectURI, @RequestParam String state) {
    GitHubAppInstallation appInstallation = installationManager.getAppInstallation().orElseThrow(GitHubAppInstallationMissingException::new);
    if (!appInstallation.getCallbackUrl().equals(redirectURI)) {
      log.warn("Request redirect URI {} does not match app installation redirect URI {}", redirectURI, appInstallation.getCallbackUrl());
      throw new AccessDeniedException("Invalid redirect url");
    }

    return ResponseEntity.status(HttpStatus.FOUND)
        .location(UriComponentsBuilder.fromUriString(redirectURI)
            .queryParam("state", state)
            .queryParam("code", tokenService.generateTemporaryCode())
            .build()
            .toUri()
        )
        .build();
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  record OAuth2AccessTokenRequest(
      String clientId,
      String clientSecret,
      String code
  ) {

  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  record OAuth2AccessTokenResponse(
      String accessToken,
      String scope,
      String tokenType
  ) {

  }

  @PostMapping(path = "/login/oauth/access_token", consumes = MediaType.APPLICATION_JSON_VALUE)
  OAuth2AccessTokenResponse handleOAuth2AccessToken(@RequestBody OAuth2AccessTokenRequest request) {
    return issueAccessToken(request.clientId, request.clientSecret, request.code);
  }

  // GitHub also accepts a form-encoded token exchange; OAuth clients such as Sentry's send one
  @PostMapping(path = "/login/oauth/access_token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  OAuth2AccessTokenResponse handleFormOAuth2AccessToken(
      @RequestParam(name = "client_id") String clientId,
      @RequestParam(name = "client_secret") String clientSecret,
      @RequestParam String code
  ) {
    return issueAccessToken(clientId, clientSecret, code);
  }

  private OAuth2AccessTokenResponse issueAccessToken(String clientId, String clientSecret, String code) {
    String token = tokenService.generateToken(clientId, clientSecret, code);
    return new OAuth2AccessTokenResponse(
        token,
        "",
        "bearer"
    );
  }

}
