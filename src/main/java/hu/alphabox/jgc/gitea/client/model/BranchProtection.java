package hu.alphabox.jgc.gitea.client.model;

import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public record BranchProtection(
    @Nullable
    String branchName,
    @Nullable
    String ruleName,
    @Nullable
    Boolean enablePush,
    @Nullable
    Boolean enablePushWhitelist,
    @Nullable
    List<String> pushWhitelistUsernames,
    @Nullable
    Integer requiredApprovals,
    @Nullable
    Boolean enableApprovalsWhitelist,
    @Nullable
    List<String> approvalsWhitelistUsername,
    @Nullable
    Boolean blockOnRejectedReviews,
    @Nullable
    Boolean blockOnOfficialReviewRequests,
    @Nullable
    Boolean blockOnOutdatedBranch,
    @Nullable
    Boolean dismissStaleApprovals,
    @Nullable
    Boolean requireSignedCommits,
    @Nullable
    Boolean enableStatusCheck,
    @Nullable
    List<String> statusCheckContexts,
    @Nullable
    String protectedFilePatterns,
    @Nullable
    @DateTimeFormat(iso = ISO.DATE_TIME)
    OffsetDateTime createdAt,
    @Nullable
    @DateTimeFormat(iso = ISO.DATE_TIME)
    OffsetDateTime updatedAt
) {

}
