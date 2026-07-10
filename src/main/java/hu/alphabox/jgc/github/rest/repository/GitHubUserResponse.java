package hu.alphabox.jgc.github.rest.repository;

import java.net.URI;
import java.time.OffsetDateTime;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public record GitHubUserResponse(
    String login,
    Long id,
    String nodeId,
    URI avatarUrl,
    String gravatarId,
    URI url,
    URI htmlUrl,
    URI followersUrl,
    String followingUrl,
    String gistsUrl,
    String starredUrl,
    URI subscriptionsUrl,
    URI organizationsUrl,
    URI reposUrl,
    String eventsUrl,
    URI receivedEventsUrl,
    String type,
    Boolean siteAdmin,
    String name,
    String company,
    URI blog,
    String location,
    String email,
    Boolean hireable,
    String bio,
    String twitterUsername,
    Integer publicRepos,
    Integer publicGists,
    Integer followers,
    Integer following,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

}
