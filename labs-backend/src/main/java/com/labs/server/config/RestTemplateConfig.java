package com.labs.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Single {@link RestTemplate} instance used by cross-app proxies (e.g. the
 * hospital-services controller forwarding to HMS).
 *
 * Built on Java 11+ {@link HttpClient} via {@link JdkClientHttpRequestFactory}
 * — the default {@code SimpleClientHttpRequestFactory} relies on
 * {@code HttpURLConnection} which silently strips/breaks the PATCH method on
 * most JDKs (boot's flow returned 502 on every PATCH/toggle-status). The JDK
 * HttpClient supports PATCH natively, no extra dependency needed.
 *
 * Default response error handler is replaced with a no-op so 4xx/5xx from HMS
 * are returned through {@link RestTemplate#exchange} instead of raising —
 * letting the proxy controller pass HMS's body + status byte-for-byte.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate proxyRestTemplate() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));

        RestTemplate rt = new RestTemplate(factory);
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false; // never raise; the proxy passes 4xx/5xx through verbatim
            }
        });
        return rt;
    }
}
