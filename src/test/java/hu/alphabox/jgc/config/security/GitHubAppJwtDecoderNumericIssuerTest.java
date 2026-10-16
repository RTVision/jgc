package hu.alphabox.jgc.config.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallation.Installation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import java.time.Instant;
import java.util.Date;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class GitHubAppJwtDecoderNumericIssuerTest {

  @Autowired
  private RestTestClient client;

  @Autowired
  private GitHubAppInstallationManager installationManager;

  @MockitoBean
  private GiteaClient giteaClient;

  /**
   * Octokit's app auth writes the app id into the iss claim as a JSON number instead of a
   * string. GitHub accepts both, so the decoder has to as well.
   */
  @Test
  void shouldAcceptJwtWithNumericIssuerClaim() throws JOSEException {
    GitHubAppInstallation appInstallation = installationManager.getAppInstallation().orElseThrow();
    Installation installation = appInstallation.getInstallations().iterator().next();

    client.post()
        .uri("/api/v3/app/installations/{installation-id}/access_tokens", installation.getId())
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(jwtWithIssuer(appInstallation, appInstallation.getId())))
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals("X-GitHub-Enterprise-Version", "3.17.6");
  }

  /**
   * GitHub returns expires_at in whole seconds, and Sentry parses it with a fraction-less
   * format, so a nanosecond timestamp broke its repository calls.
   */
  @Test
  void shouldReturnExpiresAtInWholeSeconds() throws JOSEException {
    GitHubAppInstallation appInstallation = installationManager.getAppInstallation().orElseThrow();
    Installation installation = appInstallation.getInstallations().iterator().next();

    client.post()
        .uri("/api/v3/app/installations/{installation-id}/access_tokens", installation.getId())
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(jwtWithIssuer(appInstallation, appInstallation.getId())))
        .exchange()
        .expectStatus().isCreated()
        .expectBody().jsonPath("$.expires_at").value(String.class,
            expiresAt -> Assertions.assertThat(expiresAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"));
  }

  @Test
  void shouldRejectJwtWithUnknownNumericIssuerClaim() throws JOSEException {
    GitHubAppInstallation appInstallation = installationManager.getAppInstallation().orElseThrow();
    Installation installation = appInstallation.getInstallations().iterator().next();

    client.post()
        .uri("/api/v3/app/installations/{installation-id}/access_tokens", installation.getId())
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(jwtWithIssuer(appInstallation, appInstallation.getId() + 1)))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private String jwtWithIssuer(GitHubAppInstallation appInstallation, long issuer) throws JOSEException {
    Instant now = Instant.now();
    JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
        .claim("iss", issuer)
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plusSeconds(600)))
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(),
        claimsSet
    );
    jwt.sign(new RSASSASigner(JWK.parseFromPEMEncodedObjects(appInstallation.getPrivateKey()).toRSAKey()));
    return jwt.serialize();
  }
}
