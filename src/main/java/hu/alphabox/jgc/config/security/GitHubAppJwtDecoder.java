package hu.alphabox.jgc.config.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.SignedJWT;
import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import java.text.ParseException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuedAtValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class GitHubAppJwtDecoder implements JwtDecoder {

  private final GitHubAppInstallationManager githubAppInstallationManager;

  private final MappedJwtClaimSetConverter claimSetConverter = MappedJwtClaimSetConverter.withDefaults(Collections.emptyMap());
  private final OAuth2TokenValidator<Jwt> jwtValidator;

  public GitHubAppJwtDecoder(GitHubAppInstallationManager githubAppInstallationManager) {
    this.githubAppInstallationManager = githubAppInstallationManager;

    JwtIssuedAtValidator jwtIssuedAtValidator = new JwtIssuedAtValidator(true);
    jwtIssuedAtValidator.setClockSkew(Duration.ofMinutes(2));

    jwtValidator = new DelegatingOAuth2TokenValidator<>(
        jwtIssuedAtValidator,
        new JwtTimestampValidator(Duration.ofMinutes(10))
    );
  }

  @Override
  public Jwt decode(String token) throws JwtException {
    Optional<GitHubAppInstallation> optionalAppInstallation = githubAppInstallationManager.getAppInstallation();
    if (optionalAppInstallation.isEmpty()) {
      log.warn("GitHub App Installation not found for JWT token validation.");
      throw new BadJwtException("Failed to validate token.");
    }
    GitHubAppInstallation appInstallation = optionalAppInstallation.get();

    try {
      SignedJWT signedJWT = SignedJWT.parse(token);

      if (!signedJWT.getHeader().getAlgorithm().equals(JWSAlgorithm.RS256)) {
        log.warn("Signed JWT rejected: Invalid signature algorithm.");
        throw new JwtException("Signed JWT rejected: Invalid signature algorithm.");
      }

      // Octokit sends the app id as a JSON number in the iss claim; GitHub accepts both, but
      // Nimbus's strict JWTClaimsSet parsing rejects a non-string iss, so read the raw payload.
      Map<String, Object> payload = signedJWT.getPayload().toJSONObject();
      if (payload == null) {
        log.warn("Signed JWT rejected: payload is not a JSON object.");
        throw new BadJwtException("Failed to parse token claims.");
      }

      if (!appInstallation.getId().toString().equals(String.valueOf(payload.get("iss")))) {
        log.warn("Invalid issuer in token.");
        throw new BadJwtException("Invalid issuer in token.");
      }

      JWK jwk = JWK.parseFromPEMEncodedObjects(appInstallation.getPublicKey());

      if (!signedJWT.verify(new RSASSAVerifier(jwk.toRSAKey()))) {
        log.warn("Signed JWT rejected: Invalid signature");
        throw new JwtException("Signed JWT rejected: Invalid signature");
      }

      Map<String, Object> claims = claimSetConverter.convert(payload);
      Jwt jwt = Jwt.withTokenValue(token)
          .headers(h -> h.putAll(signedJWT.getHeader().toJSONObject()))
          .claims(c -> c.putAll(claims))
          .build();

      OAuth2TokenValidatorResult validationResult = jwtValidator.validate(jwt);
      if (validationResult.hasErrors()) {
        String joinedValidationDescriptions = validationResult.getErrors().stream()
            .map(OAuth2Error::getDescription)
            .collect(Collectors.joining(", "));

        log.warn("JWT validation failed with following errors: {}", joinedValidationDescriptions);
        throw new JwtValidationException(
            "JWT validation failed with following errors: %s".formatted(joinedValidationDescriptions),
            validationResult.getErrors()
        );
      }

      return jwt;
    } catch (ParseException e) {
      log.error("An error occurred while parsing the JWT token: {}", e.getMessage(), e);
      throw new BadJwtException("An error occurred while parsing the token.", e);
    } catch (JOSEException e) {
      log.error("Cannot retrieve the public key to check JWT signature: {}", e.getMessage(), e);
      throw new BadJwtException("Cannot retrieve the public key to check signature.", e);
    }
  }
}
