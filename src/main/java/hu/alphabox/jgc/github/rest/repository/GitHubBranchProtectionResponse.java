package hu.alphabox.jgc.github.rest.repository;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
record GitHubBranchProtectionResponse(
    RequiredPullRequestReviews requiredPullRequestReviews,
    RequiredStatusChecks requiredStatusChecks,
    EnforceAdmins enforceAdmins,
    RequiredSignatures requiredSignatures,
    AllowForcePushes allowForcePushes,
    AllowDeletions allowDeletions
) {

  @JsonNaming(SnakeCaseStrategy.class)
  record RequiredPullRequestReviews(
      Integer requiredApprovingReviewCount,
      Boolean dismissStaleReviews,
      Boolean requireCodeOwnerReviews
  ) {

  }

  @JsonNaming(SnakeCaseStrategy.class)
  record RequiredStatusChecks(
      Boolean strict,
      List<String> contexts
  ) {

  }

  @JsonNaming(SnakeCaseStrategy.class)
  record EnforceAdmins(Boolean enabled) {

  }

  @JsonNaming(SnakeCaseStrategy.class)
  record RequiredSignatures(Boolean enabled) {

  }

  @JsonNaming(SnakeCaseStrategy.class)
  record AllowForcePushes(Boolean enabled) {

  }

  @JsonNaming(SnakeCaseStrategy.class)
  record AllowDeletions(Boolean enabled) {

  }
}
