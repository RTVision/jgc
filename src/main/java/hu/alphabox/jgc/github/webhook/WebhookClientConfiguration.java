package hu.alphabox.jgc.github.webhook;

import hu.alphabox.jgc.ApplicationProperties;
import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import hu.alphabox.jgc.github.webhook.WebhookException.WebhookClientException;
import hu.alphabox.jgc.github.webhook.WebhookException.WebhookServerException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
class WebhookClientConfiguration {

  @Bean
  WebhookClient webhookClient(
      Builder restClientBuilder,
      GitHubAppInstallationManager installationManager,
      ApplicationProperties applicationProperties
  ) {
    RestClient restClient = restClientBuilder
        .requestInterceptor(new WebhookInterceptor(installationManager, applicationProperties))
        .defaultStatusHandler(WebhookClientResponseErrorHandler.INSTANCE)
        .defaultStatusHandler(WebhookServerResponseErrorHandler.INSTANCE)
        .build();

    RestClientAdapter adapter = RestClientAdapter.create(restClient);
    HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

    return factory.createClient(WebhookClient.class);
  }

  @RequiredArgsConstructor
  @Slf4j
  static class WebhookInterceptor implements ClientHttpRequestInterceptor {

    private static final String HOOK_ID_HEADER = "x-github-hook-id";
    private static final String DELIVERY_HEADER = "x-github-delivery";
    private static final String SIGNATURE_HEADER = "x-hub-signature-256";
    private static final String SIGNATURE_LEGACY_HEADER = "x-hub-signature";
    private static final String USER_AGENT_HEADER = "user-agent";
    private static final String INSTALLATION_TARGET_TYPE_HEADER = "x-github-hook-installation-target-type";
    private static final String INSTALLATION_TARGET_ID_HEADER = "x-github-hook-installation-target-id";
    private static final String ENTERPRISE_VERSION_HEADER = "x-github-enterprise-version";
    private static final String ENTERPRISE_HOST_HEADER = "x-github-enterprise-host";

    private static final String GITHUB_ENTERPRISE_VERSION = "3.17.6";

    private final GitHubAppInstallationManager installationManager;
    private final ApplicationProperties applicationProperties;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
      Optional<GitHubAppInstallation> appInstallationOptional = installationManager.getAppInstallation();
      if (appInstallationOptional.isEmpty()) {
        throw new IllegalStateException("No installation context available, request aborted.");
      }

      GitHubAppInstallation appInstallation = appInstallationOptional.get();
      if (appInstallation.getWebhookUrl() == null || appInstallation.getWebhookUrl().isBlank()) {
        throw new IllegalStateException("The app was registered without a webhook URL, request aborted.");
      }

      request.getHeaders().set(SIGNATURE_HEADER, "sha256=" + WebhookSignature.generateSignature(body, appInstallation.getWebhookSecret()));
      request.getHeaders().set(SIGNATURE_LEGACY_HEADER, "sha1=" + WebhookSignature.generateLegacySignature(body, appInstallation.getWebhookSecret()));
      request.getHeaders().set(INSTALLATION_TARGET_TYPE_HEADER, "integration");
      request.getHeaders().set(INSTALLATION_TARGET_ID_HEADER, String.valueOf(appInstallation.getId()));
      request.getHeaders().set(HOOK_ID_HEADER, Long.toString(1L));
      request.getHeaders().set(ENTERPRISE_VERSION_HEADER, GITHUB_ENTERPRISE_VERSION);
      request.getHeaders().set(ENTERPRISE_HOST_HEADER, applicationProperties.baseUrl());
      request.getHeaders().set(DELIVERY_HEADER, UUID.randomUUID().toString());
      request.getHeaders().set(USER_AGENT_HEADER, "GitHub-Hookshot/044aadd");

      HttpRequestWrapper wrapper = new HttpRequestWrapper(request) {
        @Override
        public URI getURI() {
          return URI.create(appInstallation.getWebhookUrl());
        }
      };

      log.debug(
          "Sending webhook request to: {} [headers: {}, body: {}]",
          appInstallation.getWebhookUrl(),
          wrapper.getHeaders(),
          new String(body)
      );
      ClientHttpResponse response = execution.execute(wrapper, body);
      if (!response.getStatusCode().isError()) {
        String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        log.info("Webhook successfully processed with status: {}, response: {}", response.getStatusCode(), responseBody);
      }
      return response;
    }
  }

  @Slf4j
  static class WebhookClientResponseErrorHandler implements ResponseErrorHandler {

    static final WebhookClientResponseErrorHandler INSTANCE = new WebhookClientResponseErrorHandler();

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
      return response.getStatusCode().is4xxClientError();
    }

    @Override
    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
      String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
      log.error("Webhook callee related (HTTP {}) exception occurred with message: {}", response.getStatusCode(), responseBody);
      throw new WebhookClientException(responseBody);
    }
  }

  @Slf4j
  static class WebhookServerResponseErrorHandler implements ResponseErrorHandler {

    static final WebhookServerResponseErrorHandler INSTANCE = new WebhookServerResponseErrorHandler();

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
      return response.getStatusCode().is5xxServerError();
    }

    @Override
    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
      String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
      log.error("Webhook server related (HTTP {}) exception occurred with message: {}", response.getStatusCode(), responseBody);
      throw new WebhookServerException(responseBody);
    }
  }

}
