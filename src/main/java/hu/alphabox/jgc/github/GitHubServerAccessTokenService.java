package hu.alphabox.jgc.github;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import hu.alphabox.jgc.ApplicationProperties;
import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationInvalidClientException;
import hu.alphabox.jgc.github.app.GitHubAppInstallationInvalidGrantException;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import hu.alphabox.jgc.github.app.GitHubAppInstallationMissingException;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;


/**
 * @see <a
 * href="https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-an-installation-access-token-for-a-github-app">
 * Generating Installation access token
 * </a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubServerAccessTokenService implements InitializingBean {

  private static final Duration TOKEN_VALIDITY_DURATION = Duration.ofHours(1);

  private final JsonMapper jsonMapper;
  private final ApplicationProperties applicationProperties;
  private final GitHubAppInstallationManager appInstallationManager;
  private final Cache<String, OpaqueToken> opaqueTokens = Caffeine.newBuilder().expireAfterWrite(TOKEN_VALIDITY_DURATION).build();

  public record OpaqueToken(
      String token,
      Instant expiresAt,
      Long installationId
  ) {

  }

  @Override
  public void afterPropertiesSet() {
    File tokenFile = tokenFile();
    if (!tokenFile.exists()) {
      return;
    }

    PersistentOpaqueTokenList persistentTokens = jsonMapper.readValue(tokenFile, PersistentOpaqueTokenList.class);
    Instant now = Instant.now();
    for (OpaqueToken opaqueToken : persistentTokens.tokens()) {
      if (opaqueToken.expiresAt().isAfter(now)) {
        opaqueTokens.put(opaqueToken.token(), opaqueToken);
      }
    }
  }

  public OpaqueToken generateToken(Authentication authentication, Long installationId) {
    GitHubAppInstallation appInstallation = appInstallationManager.getAppInstallation().orElseThrow(GitHubAppInstallationMissingException::new);

    if (!(authentication instanceof JwtAuthenticationToken authenticationToken)) {
      throw new GitHubAppInstallationInvalidGrantException();
    }

    Jwt jwtToken = authenticationToken.getToken();
    if (
        !appInstallation.getId().toString().equals(jwtToken.getClaimAsString(JwtClaimNames.ISS))
            || appInstallation.getInstallation(installationId).isEmpty()
    ) {
      throw new GitHubAppInstallationInvalidClientException();
    }

    String token = GitHubStyleTokenGenerator.generateToken("ghs");

    OpaqueToken opaqueToken = new OpaqueToken(token, Instant.now().plus(TOKEN_VALIDITY_DURATION), installationId);
    opaqueTokens.put(token, opaqueToken);
    save();

    return opaqueToken;
  }

  public Optional<OpaqueToken> getToken(String token) {
    return Optional.ofNullable(opaqueTokens.getIfPresent(token));
  }

  private void save() {
    try {
      List<OpaqueToken> persistentTokens = new ArrayList<>(opaqueTokens.asMap().values());
      jsonMapper.writerWithDefaultPrettyPrinter().writeValue(tokenFile(), new PersistentOpaqueTokenList(persistentTokens));
    } catch (Exception e) {
      log.warn("Failed to persist GitHub Server access tokens.", e);
    }
  }

  private File tokenFile() {
    return new File(applicationProperties.installationPath() + ".tokens.json");
  }

  private record PersistentOpaqueTokenList(
      List<OpaqueToken> tokens
  ) {

    private PersistentOpaqueTokenList {
      if (tokens == null) {
        tokens = List.of();
      }
    }

  }

}
