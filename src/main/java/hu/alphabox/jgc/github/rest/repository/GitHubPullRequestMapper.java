package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.TypeConversionMapper;
import hu.alphabox.jgc.config.MapperCentralConfig;
import hu.alphabox.jgc.gitea.client.model.ChangedFile;
import hu.alphabox.jgc.gitea.client.model.PRBranchInfo;
import hu.alphabox.jgc.gitea.client.model.PullRequest;
import hu.alphabox.jgc.gitea.client.model.PullReview;
import hu.alphabox.jgc.gitea.client.model.Repository;
import hu.alphabox.jgc.gitea.client.model.Team.PermissionEnum;
import hu.alphabox.jgc.gitea.client.model.User;
import hu.alphabox.jgc.github.rest.repository.GitHubPullRequestRequestedReviewersResponse.SimpleUser;
import hu.alphabox.jgc.github.rest.repository.GitHubPullRequestRequestedReviewersResponse.Team;
import hu.alphabox.jgc.github.rest.repository.GitHubPullRequestReviewResponse.State;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;

@Mapper(
    config = MapperCentralConfig.class,
    uses = TypeConversionMapper.class
)
interface GitHubPullRequestMapper {

  default GitHubPullRequestRequestedReviewersResponse map(List<PullReview> pullReviews) {
    List<SimpleUser> users = pullReviews.stream()
        .filter(pr -> pr.user() != null)
        .collect(Collectors.groupingBy(PullReview::user))
        .entrySet().stream()
        .filter(entry -> entry.getValue().size() == 1)
        .filter(entry -> "REQUEST_REVIEW".equals(entry.getValue().getFirst().state()))
        .map(Map.Entry::getKey)
        .map(this::map)
        .toList();

    List<Team> teams = pullReviews.stream()
        .filter(pr -> pr.team() != null)
        .collect(Collectors.groupingBy(PullReview::team))
        .entrySet().stream()
        .filter(entry -> entry.getValue().size() == 1)
        .filter(entry -> "REQUEST_REVIEW".equals(entry.getValue().getFirst().state()))
        .map(Map.Entry::getKey)
        .map(this::map)
        .toList();

    return new GitHubPullRequestRequestedReviewersResponse(users, teams);
  }

  @ValueMapping(target = "APPROVED", source = "APPROVED")
  @ValueMapping(target = "PENDING", source = "PENDING")
  @ValueMapping(target = "PENDING", source = "REQUEST_REVIEW")
  @ValueMapping(target = "COMMENTED", source = "COMMENT")
  @ValueMapping(target = "CHANGES_REQUESTED", source = "REQUEST_CHANGES")
  @ValueMapping(target = MappingConstants.THROW_EXCEPTION, source = MappingConstants.ANY_REMAINING)
  State state(String state);

  @Mapping(target = "name", source = "fullName")
  @Mapping(target = "siteAdmin", source = "isAdmin")
  @Mapping(target = "type", constant = "User")
  SimpleUser map(User user);

  @Mapping(target = "permission", source = "permission")
  Team map(hu.alphabox.jgc.gitea.client.model.Team team);

  default String permission(PermissionEnum permission) {
    return permission == null ? null : permission.name().toLowerCase(Locale.ENGLISH);
  }

  List<GitHubPullRequestReviewResponse> reviews(List<PullReview> review);

  @Mapping(target = "name", source = "fullName")
  @Mapping(target = "siteAdmin", source = "isAdmin")
  @Mapping(target = "type", constant = "User")
  GitHubPullRequestReviewResponse.SimpleUser reviewUser(User user);

  List<GitHubPullRequestResponse> pullRequests(List<PullRequest> pullRequests);

  GitHubPullRequestResponse pullRequest(PullRequest pullRequest);

  @Mapping(target = "ref", source = "label")
  GitHubPullRequestResponse.Branch branch(PRBranchInfo branchInfo);

  GitHubPullRequestResponse.Repo repo(Repository repository);

  default GitHubPullRequestFileResponse pullRequestFile(ChangedFile file) {
    return new GitHubPullRequestFileResponse(
        null,
        file.filename(),
        file.status(),
        file.additions(),
        file.deletions(),
        file.changes(),
        file.rawUrl(),
        file.contentsUrl(),
        file.previousFilename(),
        file.patch()
    );
  }

  List<GitHubPullRequestFileResponse> pullRequestFiles(List<ChangedFile> files);
}
