package hu.alphabox.jgc.gitea.webhook;

import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.webhook.GiteaWebhookPushEvent.Commit;
import hu.alphabox.jgc.github.webhook.WebhookService;
import jakarta.validation.Valid;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(GiteaWebhookHandler.API_MAPPING + "/{installation-id}")
@RequiredArgsConstructor
public class GiteaWebhookHandler {

  public static final String API_MAPPING = "/api/gitea/webhook";
  private final WebhookService webhookService;
  private final AllowedRepositoryService allowedRepositoryService;

  @PostMapping(headers = {"x-gitea-event=push"})
  void handlePushEvent(
      @PathVariable("installation-id") Integer installationId,
      @RequestBody @Valid GiteaWebhookPushEvent event
  ) {
    if (!isAllowedRepository(event.repository().fullName())) {
      return;
    }

    log.info(
        "Push event occurred in repository: {}, commits: {}, installation id: {}",
        event.repository().fullName(),
        event.commits().stream()
            .map(Commit::id)
            .collect(Collectors.toSet()),
        installationId
    );
    log.debug("Received event: {}", event);
    webhookService.convertAndSend(event, installationId);
  }

  @PostMapping(headers = {"x-gitea-event=create"})
  void handleBranchCreationEvent(
      @PathVariable("installation-id") Integer installationId,
      @RequestBody @Valid GiteaWebhookBranchCreationEvent event
  ) {
    if (!isAllowedRepository(event.repository().fullName())) {
      return;
    }

    if (event.refType() == GiteaEntityReferenceType.BRANCH) {
      log.info(
          "Branch creation event occurred in repository: {}, ref: {} ({}), installation id: {}",
          event.repository().fullName(),
          event.ref(),
          event.sha(),
          installationId
      );
      log.debug("Received event: {}", event);
      webhookService.convertAndSend(event, installationId);
    } else {
      log.trace("Tag creation event occurred.");
      log.trace("Received event: {}", event);
    }
  }

  @PostMapping(headers = {"x-gitea-event=delete"})
  void handleBranchDeletionEvent(
      @PathVariable("installation-id") Integer installationId,
      @RequestBody @Valid GiteaWebhookBranchDeletionEvent event
  ) {
    if (!isAllowedRepository(event.repository().fullName())) {
      return;
    }

    if (event.refType() == GiteaEntityReferenceType.BRANCH) {
      log.info(
          "Branch deletion event occurred in repository: {}, ref: {}, installation id: {}",
          event.repository().fullName(),
          event.ref(),
          installationId
      );
      log.debug("Received event: {}", event);
      webhookService.convertAndSend(event, installationId);
    } else {
      log.trace("Tag deletion event occurred.");
      log.trace("Received event: {}", event);
    }
  }

  @PostMapping(headers = {"x-gitea-event=repository"})
  void handleRepositoryEvent(
      @PathVariable("installation-id") Integer installationId,
      @RequestBody @Valid GiteaWebhookRepositoryEvent event
  ) {
    if (!isAllowedRepository(event.repository().fullName())) {
      return;
    }

    log.info(
        "Repository event occurred, repository: {}, action: {}, installation id: {}",
        event.repository().fullName(),
        event.action(),
        installationId
    );
    log.debug("Received event: {}", event);
    webhookService.convertAndSend(event, installationId);
  }

  @PostMapping(headers = {
      "x-gitea-event=pull_request",
      "x-gitea-event-type=pull_request"
  })
  void handlePullRequestEvent(
      @PathVariable("installation-id") Integer installationId,
      @RequestBody @Valid GiteaWebhookPullRequestEvent event
  ) {
    if (!isAllowedRepository(event.repository().fullName())) {
      return;
    }

    log.info(
        "Pull request event occurred, repository: {}, action: {}, PR number: {}, installation id: {}",
        event.repository().fullName(),
        event.action(),
        event.pullRequest().number(),
        installationId
    );
    log.debug("Received event: {}", event);
    webhookService.convertAndSendPullRequest(event, installationId);
  }

  @PostMapping(headers = {
      "x-gitea-event=pull_request",
      "x-gitea-event-type=pull_request_review_request"
  })
  void handlePullRequestReviewRequestEvent(
      @PathVariable("installation-id") Integer installationId,
      @RequestBody @Valid GiteaWebhookPullRequestEvent event
  ) {
    if (!isAllowedRepository(event.repository().fullName())) {
      return;
    }

    log.info(
        "Review request for pull request event occurred, repository: {}, action: {}, PR number: {}, installation id: {}",
        event.repository().fullName(),
        event.action(),
        event.pullRequest().number(),
        installationId
    );
    log.debug("Received event: {}", event);
    webhookService.convertAndSendPullRequest(event, installationId);
  }

  @PostMapping(headers = {
      "x-gitea-event=issue_comment",
      "x-gitea-event-type=pull_request_comment"
  })
  void handlePullRequestCommentEvent(
      @PathVariable("installation-id") Integer installationId,
      @RequestBody @Valid GiteaWebhookPullRequestEvent event
  ) {
    if (!isAllowedRepository(event.repository().fullName())) {
      return;
    }

    log.info(
        "Pull request comment event occurred, repository: {}, action: {}, PR number: {}, installation id: {}",
        event.repository().fullName(),
        event.action(),
        event.pullRequest().number(),
        installationId
    );
    log.debug("Received event: {}", event);

    if (event.issue() != null) {
      webhookService.convertAndSendIssueComment(event, installationId);
    } else {
      webhookService.convertAndSendPullRequestReview(event, installationId, "edited");
    }
  }

  @PostMapping(headers = {
      "x-gitea-event=pull_request_rejected",
      "x-gitea-event-type=pull_request_review_rejected"
  })
  void handlePullRequestReviewRejectedEvent(
      @PathVariable("installation-id") Integer installationId,
      @RequestBody @Valid GiteaWebhookPullRequestEvent event
  ) {
    if (!isAllowedRepository(event.repository().fullName())) {
      return;
    }

    log.info(
        "Pull request review rejected event occurred, repository: {}, action: {}, PR number: {}, installation id: {}",
        event.repository().fullName(),
        event.action(),
        event.pullRequest().number(),
        installationId
    );
    log.debug("Received event: {}", event);
    webhookService.convertAndSendPullRequestReview(event, installationId, "submitted");
  }

  @PostMapping(headers = {
      "x-gitea-event=pull_request_approved",
      "x-gitea-event-type=pull_request_review_approved"
  })
  void handlePullRequestReviewApprovedEvent(
      @PathVariable("installation-id") Integer installationId,
      @RequestBody @Valid GiteaWebhookPullRequestEvent event
  ) {
    if (!isAllowedRepository(event.repository().fullName())) {
      return;
    }

    log.info(
        "Pull request review approved event occurred, repository: {}, action: {}, PR number: {}, installation id: {}",
        event.repository().fullName(),
        event.action(),
        event.pullRequest().number(),
        installationId
    );
    log.debug("Received event: {}", event);
    webhookService.convertAndSendPullRequestReview(event, installationId, "submitted");
  }

  @PostMapping(headers = {
      "x-gitea-event=pull_request_comment",
      "x-gitea-event-type=pull_request_review_comment"
  })
  void handlePullRequestReviewCommentEvent(
      @PathVariable("installation-id") Integer installationId,
      @RequestBody @Valid GiteaWebhookPullRequestEvent event
  ) {
    if (!isAllowedRepository(event.repository().fullName())) {
      return;
    }

    log.info(
        "Pull request review comment event occurred, repository: {}, action: {}, PR number: {}, installation id: {}",
        event.repository().fullName(),
        event.action(),
        event.pullRequest().number(),
        installationId
    );
    log.debug("Received event: {}", event);
    webhookService.convertAndSendPullRequestReview(event, installationId, "edited");
  }

  private boolean isAllowedRepository(String repositoryFullName) {
    return allowedRepositoryService.isAllowed(repositoryFullName);
  }

}
