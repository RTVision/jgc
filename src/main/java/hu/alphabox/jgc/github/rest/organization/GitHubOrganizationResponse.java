package hu.alphabox.jgc.github.rest.organization;

import java.net.URI;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
record GitHubOrganizationResponse(
    String login,
    Long id,
    String description,
    URI avatarUrl,
    URI htmlUrl,
    String type
) {

}
