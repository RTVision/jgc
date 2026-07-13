package hu.alphabox.jgc.github.rest.repository;

import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
record GitHubOrganizationMembershipResponse(
    String state,
    String role,
    User user,
    Organization organization
) {

  @JsonNaming(SnakeCaseStrategy.class)
  record User(
      String login,
      Long id
  ) {

  }

  @JsonNaming(SnakeCaseStrategy.class)
  record Organization(
      String login,
      Long id
  ) {

  }
}
