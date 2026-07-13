package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.Organization;
import hu.alphabox.jgc.gitea.client.model.User;
import hu.alphabox.jgc.github.GitHubUserAccessTokenService;
import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class GitHubUserMembershipCallHandlerTest {

  @Autowired
  private RestTestClient client;

  @Autowired
  private GitHubAppInstallationManager installationManager;

  @MockitoBean
  private GiteaClient giteaClient;

  @Autowired
  private GitHubUserAccessTokenService userTokenService;

  // the endpoint sits in the user-token security chain, so authenticate with a ghu_ token
  private String token() {
    GitHubAppInstallation appInstallation = installationManager.getAppInstallation().orElseThrow();
    String code = userTokenService.generateTemporaryCode();
    return userTokenService.generateToken(appInstallation.getClientId(), appInstallation.getClientSecret(), code);
  }

  @Test
  void shouldListOrganizationMembershipsForCurrentUser() {
    Mockito.when(giteaClient.userGetCurrent())
        .thenReturn(ResponseEntity.ok(new User(
            null, null, null, null, "The Octocat", null, 5L, null, null, "octocat"
        )));
    Mockito.when(giteaClient.orgListCurrentUserOrgs(Mockito.any(), Mockito.any()))
        .thenReturn(ResponseEntity.ok(List.of(new Organization(null, null, 7L, "TestOrg"))));

    var result = client.get()
        .uri("/api/v3/user/memberships/orgs")
        .header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token()))
        .exchangeSuccessfully()
        .returnResult(GitHubOrganizationMembershipResponse[].class)
        .getResponseBody();

    Assertions.assertThat(result).hasSize(1);
    Assertions.assertThat(result[0].state()).isEqualTo("active");
    Assertions.assertThat(result[0].role()).isEqualTo("admin");
    Assertions.assertThat(result[0].organization().login()).isEqualTo("TestOrg");
    Assertions.assertThat(result[0].organization().id()).isEqualTo(7L);
    Assertions.assertThat(result[0].user().login()).isEqualTo("octocat");
  }

}
