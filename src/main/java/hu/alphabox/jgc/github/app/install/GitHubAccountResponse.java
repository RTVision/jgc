package hu.alphabox.jgc.github.app.install;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Mirrors GitHub's simple-user schema; every field in its required list must be present, because
 * integrations may validate responses against GitHub's generated types.
 */
@JsonNaming(SnakeCaseStrategy.class)
record GitHubAccountResponse(
    Long id,
    String login,
    String nodeId,
    String avatarUrl,
    String gravatarId,
    String url,
    String htmlUrl,
    String followersUrl,
    String followingUrl,
    String gistsUrl,
    String starredUrl,
    String subscriptionsUrl,
    String organizationsUrl,
    String reposUrl,
    String eventsUrl,
    String receivedEventsUrl,
    ActorType type,
    Boolean siteAdmin
) {

  static GitHubAccountResponse of(Long id, String login, String avatarUrl, ActorType type, String baseUrl) {
    String apiUrl = "%s/api/v3/users/%s".formatted(baseUrl, login);
    String typeName = type == ActorType.ORGANIZATION ? "Organization" : "User";
    return new GitHubAccountResponse(
        id,
        login,
        Base64.getEncoder().encodeToString("012:%s%d".formatted(typeName, id).getBytes(StandardCharsets.UTF_8)),
        avatarUrl,
        "",
        apiUrl,
        "%s/%s".formatted(baseUrl, login),
        apiUrl + "/followers",
        apiUrl + "/following{/other_user}",
        apiUrl + "/gists{/gist_id}",
        apiUrl + "/starred{/owner}{/repo}",
        apiUrl + "/subscriptions",
        apiUrl + "/orgs",
        apiUrl + "/repos",
        apiUrl + "/events{/privacy}",
        apiUrl + "/received_events",
        type,
        false
    );
  }

}
