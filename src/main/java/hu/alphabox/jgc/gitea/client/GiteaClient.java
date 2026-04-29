package hu.alphabox.jgc.gitea.client;

import hu.alphabox.jgc.gitea.client.model.Branch;
import hu.alphabox.jgc.gitea.client.model.Commit;
import hu.alphabox.jgc.gitea.client.model.ContentsResponse;
import hu.alphabox.jgc.gitea.client.model.CreateBranchRepoOption;
import hu.alphabox.jgc.gitea.client.model.CreateHookOption;
import hu.alphabox.jgc.gitea.client.model.EditIssueCommentOption;
import hu.alphabox.jgc.gitea.client.model.EditIssueOption;
import hu.alphabox.jgc.gitea.client.model.Organization;
import hu.alphabox.jgc.gitea.client.model.PullRequest;
import hu.alphabox.jgc.gitea.client.model.PullReview;
import hu.alphabox.jgc.gitea.client.model.Reference;
import hu.alphabox.jgc.gitea.client.model.Repository;
import hu.alphabox.jgc.gitea.client.model.SearchResults;
import hu.alphabox.jgc.gitea.client.model.User;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(
    accept = MediaType.APPLICATION_JSON_VALUE,
    contentType = MediaType.APPLICATION_JSON_VALUE,
    value = "/api/v1"
)
@ConcurrencyLimit(limitString = "${application.gitea.client.concurrency-limit}")
public interface GiteaClient {

  // User related functions

  /**
   * GET /user : Get the authenticated user
   *
   * @return User (status code 200)
   */
  @GetExchange(value = "/user")
  ResponseEntity<User> userGetCurrent(

  );

  /**
   * GET /users/{username} : Get a user
   *
   * @param username username of user to get (required)
   * @return User (status code 200) or APINotFound is a not found empty response (status code 404)
   */
  @GetExchange(value = "/users/{username}")
  ResponseEntity<User> userGet(
      @PathVariable("username") String username
  );

  // Repository related functions

  /**
   * GET /repos/search : Search for repositories
   *
   * @param q               keyword (optional)
   * @param topic           Limit search to repositories with keyword as topic (optional)
   * @param includeDesc     include search of keyword within repository description (optional)
   * @param uid             search only for repos that the user with the given id owns or contributes to (optional)
   * @param priorityOwnerId repo owner to prioritize in the results (optional)
   * @param teamId          search only for repos that belong to the given team id (optional)
   * @param starredBy       search only for repos that the user with the given id has starred (optional)
   * @param _private        include private repositories this user has access to (defaults to true) (optional)
   * @param isPrivate       show only pubic, private or all repositories (defaults to all) (optional)
   * @param template        include template repositories this user has access to (defaults to true) (optional)
   * @param archived        show only archived, non-archived or all repositories (defaults to all) (optional)
   * @param mode            type of repository to search for. Supported values are \&quot;fork\&quot;, \&quot;source\&quot;, \&quot;mirror\&quot; and
   *                        \&quot;collaborative\&quot; (optional)
   * @param exclusive       if &#x60;uid&#x60; is given, search only for repos that the user owns (optional)
   * @param sort            sort repos by attribute. Supported values are \&quot;alpha\&quot;, \&quot;created\&quot;, \&quot;updated\&quot;, \&quot;size\&quot;,
   *                        \&quot;git_size\&quot;, \&quot;lfs_size\&quot;, \&quot;stars\&quot;, \&quot;forks\&quot; and \&quot;id\&quot;. Default is
   *                        \&quot;alpha\&quot; (optional)
   * @param order           sort order, either \&quot;asc\&quot; (ascending) or \&quot;desc\&quot; (descending). Default is \&quot;asc\&quot;, ignored if
   *                        \&quot;sort\&quot; is not specified. (optional)
   * @param page            page number of results to return (1-based) (optional)
   * @param limit           page size of results (optional)
   * @return SearchResults (status code 200) or APIValidationError is error format response related to input validation (status code 422)
   */
  @GetExchange(value = "/repos/search")
  ResponseEntity<SearchResults> repoSearch(
      @RequestParam(value = "q", required = false) @Nullable String q,
      @RequestParam(value = "topic", required = false) @Nullable Boolean topic,
      @RequestParam(value = "includeDesc", required = false) @Nullable Boolean includeDesc,
      @RequestParam(value = "uid", required = false) @Nullable Long uid,
      @RequestParam(value = "priority_owner_id", required = false) @Nullable Long priorityOwnerId,
      @RequestParam(value = "team_id", required = false) @Nullable Long teamId,
      @RequestParam(value = "starredBy", required = false) @Nullable Long starredBy,
      @RequestParam(value = "private", required = false) @Nullable Boolean _private,
      @RequestParam(value = "is_private", required = false) @Nullable Boolean isPrivate,
      @RequestParam(value = "template", required = false) @Nullable Boolean template,
      @RequestParam(value = "archived", required = false) @Nullable Boolean archived,
      @RequestParam(value = "mode", required = false) @Nullable String mode,
      @RequestParam(value = "exclusive", required = false) @Nullable Boolean exclusive,
      @RequestParam(value = "sort", required = false) @Nullable String sort,
      @RequestParam(value = "order", required = false) @Nullable String order,
      @RequestParam(value = "page", required = false) @Nullable Integer page,
      @RequestParam(value = "limit", required = false) @Nullable Integer limit
  );

  /**
   * GET /repos/{owner}/{repo} : Get a repository
   *
   * @param owner owner of the repo (required)
   * @param repo  name of the repo (required)
   * @return Repository (status code 200) or APINotFound is a not found empty response (status code 404)
   */
  @GetExchange(value = "/repos/{owner}/{repo}")
  ResponseEntity<Repository> repoGet(
      @PathVariable("owner") String owner,
      @PathVariable("repo") String repo
  );

  /**
   * GET /repos/{owner}/{repo}/contents/{filepath} : Gets the metadata and contents (if a file) of an entry in a repository, or a list of entries if a dir
   *
   * @param owner    owner of the repo (required)
   * @param repo     name of the repo (required)
   * @param filepath path of the dir, file, symlink or submodule in the repo (required)
   * @param ref      The name of the commit/branch/tag. Default to the repository’s default branch. (optional)
   * @return ContentsResponse (status code 200) or APINotFound is a not found empty response (status code 404)
   */
  @GetExchange(value = "/repos/{owner}/{repo}/contents/{filepath}")
  ResponseEntity<ContentsResponse> repoGetContents(
      @PathVariable("owner") String owner,
      @PathVariable("repo") String repo,
      @PathVariable("filepath") String filepath,
      @RequestParam(value = "ref", required = false) @Nullable String ref
  );

  /**
   * GET /repos/{owner}/{repo}/pulls/{index} : Get a pull request
   *
   * @param owner owner of the repo (required)
   * @param repo  name of the repo (required)
   * @param index index of the pull request to get (required)
   * @return PullRequest (status code 200) or APINotFound is a not found empty response (status code 404)
   */
  @GetExchange(value = "/repos/{owner}/{repo}/pulls/{index}")
  ResponseEntity<PullRequest> repoGetPullRequest(
      @PathVariable("owner") String owner,
      @PathVariable("repo") String repo,
      @PathVariable("index") Long index
  );

  /**
   * GET /repos/{owner}/{repo}/pulls : List a repo&#39;s pull requests
   *
   * @param owner      Owner of the repo (required)
   * @param repo       Name of the repo (required)
   * @param baseBranch Filter by target base branch of the pull request (optional)
   * @param state      State of pull request (optional, default to open)
   * @param sort       Type of sort (optional)
   * @param milestone  ID of the milestone (optional)
   * @param labels     Label IDs (optional)
   * @param poster     Filter by pull request author (optional)
   * @param page       Page number of results to return (1-based) (optional, default to 1)
   * @param limit      Page size of results (optional)
   * @return PullRequestList (status code 200) or APINotFound is a not found empty response (status code 404) or APIError is error format response (status code
   * 500)
   */
  @GetExchange(value = "/repos/{owner}/{repo}/pulls")
  ResponseEntity<List<PullRequest>> repoListPullRequests(
      @PathVariable("owner") String owner,
      @PathVariable("repo") String repo,
      @RequestParam(value = "base_branch", required = false) @Nullable String baseBranch,
      @RequestParam(value = "state", required = false, defaultValue = "open") String state,
      @RequestParam(value = "sort", required = false) @Nullable String sort,
      @RequestParam(value = "milestone", required = false) @Nullable Long milestone,
      @RequestParam(value = "labels", required = false) @Nullable List<Long> labels,
      @RequestParam(value = "poster", required = false) @Nullable String poster,
      @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
      @RequestParam(value = "limit", required = false) @Nullable Integer limit
  );

  /**
   * GET /repos/{owner}/{repo}/pulls/{index}/reviews : List all reviews for a pull request
   *
   * @param owner owner of the repo (required)
   * @param repo  name of the repo (required)
   * @param index index of the pull request (required)
   * @param page  page number of results to return (1-based) (optional)
   * @param limit page size of results (optional)
   * @return PullReviewList (status code 200) or APINotFound is a not found empty response (status code 404)
   */
  @GetExchange(value = "/repos/{owner}/{repo}/pulls/{index}/reviews")
  ResponseEntity<List<PullReview>> repoListPullReviews(
      @PathVariable("owner") String owner,
      @PathVariable("repo") String repo,
      @PathVariable("index") Long index,
      @RequestParam(value = "page", required = false) @Nullable Integer page,
      @RequestParam(value = "limit", required = false) @Nullable Integer limit
  );

  /**
   * GET /repos/{owner}/{repo}/pulls/{index}/commits : Get commits for a pull request
   *
   * @param owner        owner of the repo (required)
   * @param repo         name of the repo (required)
   * @param index        index of the pull request to get (required)
   * @param page         page number of results to return (1-based) (optional)
   * @param limit        page size of results (optional)
   * @param verification include verification for every commit (disable for speedup, default &#39;true&#39;) (optional)
   * @param files        include a list of affected files for every commit (disable for speedup, default &#39;true&#39;) (optional)
   * @return CommitList (status code 200) or APINotFound is a not found empty response (status code 404)
   */
  @GetExchange(value = "/repos/{owner}/{repo}/pulls/{index}/commits")
  ResponseEntity<List<Commit>> repoGetPullRequestCommits(
      @PathVariable("owner") String owner,
      @PathVariable("repo") String repo,
      @PathVariable("index") Long index,
      @RequestParam(value = "page", required = false) @Nullable Integer page,
      @RequestParam(value = "limit", required = false) @Nullable Integer limit,
      @RequestParam(value = "verification", required = false) @Nullable Boolean verification,
      @RequestParam(value = "files", required = false) @Nullable Boolean files
  );

  /**
   * GET /repos/{owner}/{repo}/commits : Get a list of all commits from a repository
   *
   * @param owner        owner of the repo (required)
   * @param repo         name of the repo (required)
   * @param sha          SHA or branch to start listing commits from (usually &#39;master&#39;) (optional)
   * @param path         filepath of a file/dir (optional)
   * @param stat         include diff stats for every commit (disable for speedup, default &#39;true&#39;) (optional)
   * @param verification include verification for every commit (disable for speedup, default &#39;true&#39;) (optional)
   * @param files        include a list of affected files for every commit (disable for speedup, default &#39;true&#39;) (optional)
   * @param page         page number of results to return (1-based) (optional)
   * @param limit        page size of results (ignored if used with &#39;path&#39;) (optional)
   * @param not          commits that match the given specifier will not be listed. (optional)
   * @return CommitList (status code 200) or APINotFound is a not found empty response (status code 404) or EmptyRepository (status code 409)
   */
  @GetExchange(value = "/repos/{owner}/{repo}/commits")
  ResponseEntity<List<Commit>> repoGetAllCommits(
      @PathVariable("owner") String owner,
      @PathVariable("repo") String repo,
      @RequestParam(value = "sha", required = false) @Nullable String sha,
      @RequestParam(value = "path", required = false) @Nullable String path,
      @RequestParam(value = "stat", required = false) @Nullable Boolean stat,
      @RequestParam(value = "verification", required = false) @Nullable Boolean verification,
      @RequestParam(value = "files", required = false) @Nullable Boolean files,
      @RequestParam(value = "page", required = false) @Nullable Integer page,
      @RequestParam(value = "limit", required = false) @Nullable Integer limit,
      @RequestParam(value = "not", required = false) @Nullable String not
  );

  /**
   * GET /repos/{owner}/{repo}/git/commits/{sha} : Get a single commit from a repository
   *
   * @param owner        owner of the repo (required)
   * @param repo         name of the repo (required)
   * @param sha          a git ref or commit sha (required)
   * @param stat         include diff stats for every commit (disable for speedup, default &#39;true&#39;) (optional)
   * @param verification include verification for every commit (disable for speedup, default &#39;true&#39;) (optional)
   * @param files        include a list of affected files for every commit (disable for speedup, default &#39;true&#39;) (optional)
   * @return Commit (status code 200) or APINotFound is a not found empty response (status code 404) or APIValidationError is error format response related to
   * input validation (status code 422)
   */
  @GetExchange(value = "/repos/{owner}/{repo}/git/commits/{sha}")
  ResponseEntity<Commit> repoGetSingleCommit(
      @PathVariable("owner") String owner,
      @PathVariable("repo") String repo,
      @PathVariable("sha") String sha,
      @RequestParam(value = "stat", required = false) @Nullable Boolean stat,
      @RequestParam(value = "verification", required = false) @Nullable Boolean verification,
      @RequestParam(value = "files", required = false) @Nullable Boolean files
  );

  /**
   * GET /repos/{owner}/{repo}/branches : List a repository&#39;s branches
   *
   * @param owner owner of the repo (required)
   * @param repo  name of the repo (required)
   * @param page  page number of results to return (1-based) (optional)
   * @param limit page size of results (optional)
   * @return BranchList (status code 200)
   */
  @GetExchange(value = "/repos/{owner}/{repo}/branches")
  ResponseEntity<List<Branch>> repoListBranches(
      @PathVariable("owner") String owner,
      @PathVariable("repo") String repo,
      @RequestParam(value = "page", required = false) @Nullable Integer page,
      @RequestParam(value = "limit", required = false) @Nullable Integer limit
  );

  /**
   * POST /repos/{owner}/{repo}/branches : Create a branch
   *
   * @param owner owner of the repo (required)
   * @param repo  name of the repo (required)
   * @param body  (optional)
   * @return Branch (status code 201) or The branch is archived or a mirror. (status code 403) or The old branch does not exist. (status code 404) or The branch
   * with the same name already exists. (status code 409) or APIRepoArchivedError is an error that is raised when an archived repo should be modified (status
   * code 423)
   */
  @PostExchange(value = "/repos/{owner}/{repo}/branches")
  ResponseEntity<Branch> repoCreateBranch(
      @PathVariable("owner") String owner,
      @PathVariable("repo") String repo,
      @RequestBody(required = false) @Nullable CreateBranchRepoOption body
  );

  /**
   * GET /repos/{owner}/{repo}/git/refs/{ref} : Get specified ref or filtered repository&#39;s refs
   *
   * @param owner owner of the repo (required)
   * @param repo  name of the repo (required)
   * @param ref   part or full name of the ref (required)
   * @return ReferenceList (status code 200) or APINotFound is a not found empty response (status code 404)
   */
  @GetExchange(value = "/repos/{owner}/{repo}/git/refs/{ref}")
  ResponseEntity<List<Reference>> repoListGitRefs(
      @PathVariable("owner") String owner,
      @PathVariable("repo") String repo,
      @PathVariable("ref") String ref
  );

  // Organization related functions

  /**
   * GET /orgs/{org} : Get an organization
   *
   * @param org name of the organization to get (required)
   * @return Organization (status code 200) or APINotFound is a not found empty response (status code 404)
   */
  @GetExchange(value = "/orgs/{org}")
  ResponseEntity<Organization> orgGet(
      @PathVariable("org") String org
  );

  /**
   * GET /orgs/{org}/repos : List an organization's repositories
   *
   * @param org   name of the organization (required)
   * @param page  page number of results to return (1-based) (optional)
   * @param limit page size of results (optional)
   * @return RepositoryList (status code 200)
   */
  @GetExchange(value = "/orgs/{org}/repos")
  ResponseEntity<List<Repository>> orgListRepos(
      @PathVariable("org") String org,
      @RequestParam(value = "page", required = false) @Nullable Integer page,
      @RequestParam(value = "limit", required = false) @Nullable Integer limit
  );

  /**
   * POST /orgs/{org}/hooks : Create a hook
   *
   * @param org  name of the organization (required)
   * @param body (required)
   * @return Hook (status code 201) or APINotFound is a not found empty response (status code 404)
   */
  @PostExchange(value = "/orgs/{org}/hooks")
  void orgCreateHook(
      @PathVariable("org") String org,
      @RequestBody CreateHookOption body
  );

  /**
   * GET /user/orgs : List the current user&#39;s organizations
   *
   * @param page  page number of results to return (1-based) (optional)
   * @param limit page size of results (optional)
   * @return OrganizationList (status code 200) or APINotFound is a not found empty response (status code 404)
   */
  @GetExchange(value = "/user/orgs")
  ResponseEntity<List<Organization>> orgListCurrentUserOrgs(
      @RequestParam(value = "page", required = false) @Nullable Integer page,
      @RequestParam(value = "limit", required = false) @Nullable Integer limit
  );

  // Issue related functions

  /**
   * PATCH /repos/{owner}/{repo}/issues/{index} : Edit an issue. If using deadline only the date will be taken into account, and time of day ignored.
   *
   * @param owner owner of the repo (required)
   * @param repo  name of the repo (required)
   * @param index index of the issue to edit (required)
   * @param body  (optional)
   * @return Issue (status code 201) or APIForbiddenError is a forbidden error response (status code 403) or APINotFound is a not found empty response (status
   * code 404) or APIError is error format response (status code 412)
   */
  @PatchExchange(value = "/repos/{owner}/{repo}/issues/{index}")
  void issueEditIssue(
      @PathVariable("owner") String owner,
      @PathVariable("repo") String repo,
      @PathVariable("index") Long index,
      @RequestBody(required = false) @Nullable EditIssueOption body
  );

  /**
   * PATCH /repos/{owner}/{repo}/issues/comments/{id} : Edit a comment
   *
   * @param owner owner of the repo (required)
   * @param repo  name of the repo (required)
   * @param id    id of the comment to edit (required)
   * @param body  (optional)
   * @return Comment (status code 200) or APIEmpty is an empty response (status code 204) or APIForbiddenError is a forbidden error response (status code 403)
   * or APINotFound is a not found empty response (status code 404) or APIRepoArchivedError is an error that is raised when an archived repo should be modified
   * (status code 423)
   */
  @PatchExchange(value = "/repos/{owner}/{repo}/issues/comments/{id}")
  void issueEditComment(
      @PathVariable("owner") String owner,
      @PathVariable("repo") String repo,
      @PathVariable("id") Long id,
      @RequestBody(required = false) @Nullable EditIssueCommentOption body
  );

}
