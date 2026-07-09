package hu.alphabox.jgc.github.rest.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GitHubRepositoryResponse(
    Long id,
    String nodeId,
    String name,
    String fullName,
    Owner owner,
    @JsonProperty("private")
    Boolean _private,
    URI htmlUrl,
    String description,
    Boolean fork,
    URI url,
    URI archiveUrl,
    URI assigneesUrl,
    URI blobsUrl,
    URI branchesUrl,
    URI collaboratorsUrl,
    URI commentsUrl,
    URI commitsUrl,
    URI compareUrl,
    URI contentsUrl,
    URI contributorsUrl,
    URI deploymentsUrl,
    URI downloadsUrl,
    URI eventsUrl,
    URI forksUrl,
    URI gitCommitsUrl,
    URI gitRefsUrl,
    URI gitTagsUrl,
    URI gitUrl,
    URI issueCommentUrl,
    URI issueEventsUrl,
    URI issuesUrl,
    URI keysUrl,
    URI labelsUrl,
    URI languagesUrl,
    URI mergesUrl,
    URI milestonesUrl,
    URI notificationsUrl,
    URI pullsUrl,
    URI releasesUrl,
    URI sshUrl,
    URI stargazersUrl,
    URI statusesUrl,
    URI subscribersUrl,
    URI subscriptionUrl,
    URI tagsUrl,
    URI teamsUrl,
    URI treesUrl,
    URI cloneUrl,
    URI mirrorUrl,
    URI hooksUrl,
    URI svnUrl,
    String homepage,
    String language,
    Integer forksCount,
    Integer stargazersCount,
    Integer watchersCount,
    Integer size,
    String defaultBranch,
    Integer openIssuesCount,
    Integer openIssues,
    Boolean isTemplate,
    List<String> topics,
    Boolean hasIssues,
    Boolean hasProjects,
    Boolean hasWiki,
    Boolean hasPages,
    Boolean hasDownloads,
    Boolean archived,
    Boolean disabled,
    String visibility,
    OffsetDateTime pushedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    Permissions permissions,
    Boolean allowRebaseMerge,
    String tempCloneToken,
    Boolean allowSquashMerge,
    Boolean allowAutoMerge,
    Boolean deleteBranchOnMerge,
    Boolean allowMergeCommit,
    Boolean allowForking,
    Boolean webCommitSignoffRequired,
    Integer subscribersCount,
    Integer networkCount,
    SecurityAndAnalysis securityAndAnalysis,
    License license,
    GitHubRepositoryResponse parent,
    GitHubRepositoryResponse source,
    Organization organization
) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  record Owner(
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
      Boolean siteAdmin
  ) {

  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  record Permissions(
      Boolean admin,
      Boolean push,
      Boolean pull
  ) {

  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  record License(
      String key,
      String name,
      URI url,
      String spdxId,
      String nodeId,
      URI htmlUrl
  ) {

  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  record SecurityAndAnalysis(
      AdvancedSecurity advancedSecurity,
      SecretScanning secretScanning,
      SecretScanningPushProtection secretScanningPushProtection,
      SecretScanningNonProviderPatterns secretScanningNonProviderPatterns
  ) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record AdvancedSecurity(String status) {

    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SecretScanning(String status) {

    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SecretScanningPushProtection(String status) {

    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SecretScanningNonProviderPatterns(String status) {

    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  record Organization(
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
      Boolean siteAdmin
  ) {

  }
}
