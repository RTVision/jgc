package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.TypeConversionMapper;
import hu.alphabox.jgc.config.MapperCentralConfig;
import hu.alphabox.jgc.gitea.client.model.Branch;
import hu.alphabox.jgc.gitea.client.model.Commit;
import hu.alphabox.jgc.gitea.client.model.CommitAffectedFiles;
import hu.alphabox.jgc.gitea.client.model.CompareResult;
import hu.alphabox.jgc.gitea.client.model.GitTreeResponse;
import hu.alphabox.jgc.gitea.client.model.Reference;
import hu.alphabox.jgc.gitea.client.model.User;
import hu.alphabox.jgc.github.rest.repository.GitHubCommitResponse.DiffEntry;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;
import org.springframework.web.util.UriComponentsBuilder;

@Mapper(
    config = MapperCentralConfig.class,
    uses = TypeConversionMapper.class
)
interface GitHubRefMapper {

  List<GitHubBranchResponse> branches(List<Branch> branch);

  @Mapping(target = "commit.sha", source = "commit.id")
  GitHubBranchResponse branch(Branch branch);

  @Mapping(target = "ref", source = "ref")
  @Mapping(target = "url", source = "branch.commit.url")
  GitHubRefResponse refFromBranch(String ref, Branch branch);

  GitHubRefResponse ref(Reference reference);

  @Mapping(target = "files", source = ".")
  GitHubCommitResponse commit(Commit commit);

  @Mapping(target = "name", source = "fullName")
  GitHubCommitResponse.User commitAuthor(User user);

  @ValueMapping(target = "ADDED", source = "added")
  @ValueMapping(target = "MODIFIED", source = "modified")
  @ValueMapping(target = "REMOVED", source = "deleted")
  @ValueMapping(target = "REMOVED", source = "removed")
  @ValueMapping(target = MappingConstants.THROW_EXCEPTION, source = MappingConstants.ANY_UNMAPPED)
  DiffEntry.Status diffEntryStatus(String status);

  default List<DiffEntry> diffEntryList(Commit commit) {
    // commits listed without files=true (list-commits, compare) carry no affected-file list
    if (commit.files() == null) {
      return Collections.emptyList();
    }

    URI baseCommitUrl = URI.create(commit.htmlUrl());
    String[] pathSegments = baseCommitUrl.getPath().split("/");

    String organization = pathSegments[1];
    String repository = pathSegments[2];
    String commitHash = pathSegments[4];

    UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
        .scheme(baseCommitUrl.getScheme())
        .host(baseCommitUrl.getHost())
        .pathSegment(
            organization,
            repository,
            "blob",
            commitHash
        );

    return commit.files().stream()
        .map(file ->
            diffEntry(
                file,
                builder
                    .cloneBuilder()
                    .pathSegment(file.filename())
                    .build()
                    .toUri()
            )
        )
        .toList();
  }

  DiffEntry diffEntry(CommitAffectedFiles commitAffectedFiles, URI blobUrl);

  default GitHubCompareResponse compare(CompareResult compareResult) {
    List<Commit> giteaCommits = compareResult.commits();
    return new GitHubCompareResponse(
        compareResult.totalCommits() == null ? 0L : compareResult.totalCommits(),
        giteaCommits == null ? Collections.emptyList() : commits(giteaCommits)
    );
  }

  List<GitHubCommitResponse> commits(List<Commit> commits);

  default GitHubTreeResponse tree(GitTreeResponse response) {
    List<GitTreeResponse.Entry> entries = response.tree();
    return new GitHubTreeResponse(
        response.sha(),
        response.url(),
        entries == null ? Collections.emptyList() : entries.stream().map(this::treeEntry).toList(),
        Boolean.TRUE.equals(response.truncated())
    );
  }

  default GitHubTreeResponse.Entry treeEntry(GitTreeResponse.Entry entry) {
    return new GitHubTreeResponse.Entry(
        entry.path(),
        entry.mode(),
        entry.type(),
        entry.sha(),
        entry.size(),
        entry.url()
    );
  }
}
