package hu.alphabox.jgc.github.app.install;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * GitHub's manifest conversion returns the full integration schema plus the credentials;
 * integrations store this object and may cross-check it against later installation lookups.
 */
@JsonNaming(SnakeCaseStrategy.class)
record AppConversionsResponse(
    Long id,
    String slug,
    String nodeId,
    GitHubAccountResponse owner,
    String name,
    String description,
    String externalUrl,
    String htmlUrl,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant updatedAt,
    Map<String, String> permissions,
    List<String> events,
    String clientId,
    String clientSecret,
    String webhookSecret,
    String pem
) {

}
