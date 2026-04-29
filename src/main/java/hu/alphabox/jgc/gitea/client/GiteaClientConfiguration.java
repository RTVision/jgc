package hu.alphabox.jgc.gitea.client;

import hu.alphabox.jgc.gitea.GiteaConfigurationProperties;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    GiteaConfigurationProperties.class,
    GiteaClientConfigurationProperties.class
})
class GiteaClientConfiguration {

  private final GiteaConfigurationProperties properties;
  private final GiteaClientConfigurationProperties clientProperties;


  @Bean
  GiteaClient giteaClient(Builder restClientBuilder) {
    RestClient restClient = restClientBuilder
        .baseUrl(properties.baseUrl())
        .requestInterceptor(new GiteaTokenAuthenticationInterceptor(clientProperties.accessToken()))
        .requestInterceptor(new GiteaRateLimitingInterceptor(clientProperties.requestDelay()))
        .build();

    RestClientAdapter adapter = RestClientAdapter.create(restClient);
    HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

    return factory.createClient(GiteaClient.class);
  }

  @Slf4j
  @RequiredArgsConstructor
  static class GiteaTokenAuthenticationInterceptor implements ClientHttpRequestInterceptor {

    private final String token;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
      request.getHeaders().setBearerAuth(token);

      log.debug(
          "Sending gitea request {} {} [headers: {}, body: {}]",
          request.getMethod(),
          request.getURI(),
          request.getHeaders(),
          new String(body)
      );
      return execution.execute(request, body);
    }
  }

  @Slf4j
  @RequiredArgsConstructor
  static class GiteaRateLimitingInterceptor implements ClientHttpRequestInterceptor {

    private final Duration requestDelay;
    private final Object lock = new Object();
    private long nextAllowedRequestAtMillis;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
      long delayMillis = requestDelay.toMillis();
      if (delayMillis > 0) {
        synchronized (lock) {
          long now = System.currentTimeMillis();
          long sleepMillis = nextAllowedRequestAtMillis - now;
          if (sleepMillis > 0) {
            try {
              Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IOException("Interrupted while throttling Gitea requests", e);
            }
            now = System.currentTimeMillis();
          }
          nextAllowedRequestAtMillis = now + delayMillis;
        }
      }

      return execution.execute(request, body);
    }
  }

}
