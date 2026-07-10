package hu.alphabox.jgc.github.rest.repository;

import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
record GitHubCollaboratorPermissionResponse(
    String permission,
    String roleName,
    GitHubUserResponse user
) {

}
