package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.Organization;
import hu.alphabox.jgc.gitea.client.model.User;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v3/user/memberships/orgs")
@RequiredArgsConstructor
class GitHubUserMembershipCallHandler {

  private final GiteaClient giteaClient;

  @GetMapping
  ResponseEntity<List<GitHubOrganizationMembershipResponse>> memberships() {
    log.info("Querying for current user's organization memberships");
    ResponseEntity<List<Organization>> response = giteaClient.orgListCurrentUserOrgs(null, null);
    if (!response.hasBody()) {
      return ResponseEntity.ok(Collections.emptyList());
    }

    User currentUser = giteaClient.userGetCurrent().getBody();
    GitHubOrganizationMembershipResponse.User user = new GitHubOrganizationMembershipResponse.User(
        currentUser.login(),
        currentUser.id()
    );

    List<GitHubOrganizationMembershipResponse> memberships = response.getBody().stream()
        .map(organization -> new GitHubOrganizationMembershipResponse(
            "active",
            "admin",
            user,
            new GitHubOrganizationMembershipResponse.Organization(organization.name(), organization.id())
        ))
        .toList();

    log.info("Found {} organization memberships for current user", memberships.size());
    return ResponseEntity.ok(memberships);
  }

}
