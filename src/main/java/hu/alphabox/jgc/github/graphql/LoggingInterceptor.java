package hu.alphabox.jgc.github.graphql;

import graphql.execution.ExecutionId;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
class LoggingInterceptor implements WebGraphQlInterceptor {

  @Override
  public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
    log.info(
        "GraphQL request received. operationName={}, id={}, document={} variables={}",
        request.getOperationName(),
        request.getId(),
        request.getDocument(),
        request.getVariables()
    );

    return chain.next(request).doOnNext(response -> {
      if (response.isValid()) {
        return;
      }

      ExecutionId executionId = response.getExecutionInput().getExecutionId();
      String validationErrors = response.getErrors().stream()
          .map(error -> "- type=%s message=%s path=%s".formatted(
              error.getErrorType(),
              error.getMessage(),
              error.getPath()
          ))
          .collect(Collectors.joining(System.lineSeparator()));

      log.warn("Validation error occurred for {}:{}{}", executionId, System.lineSeparator(), validationErrors);
    });
  }
}
