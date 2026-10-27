package com.example.expensetracker.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

@RestController
public class ServiceProxyController {
    private static final String PREFIX = "/gateway/";
    private static final Set<String> FORWARDED_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION, HttpHeaders.ACCEPT, HttpHeaders.CONTENT_TYPE, "Idempotency-Key");

    private final RestClient.Builder clientBuilder;
    private final String authServiceUrl;
    private final String summaryServiceUrl;

    public ServiceProxyController(RestClient.Builder clientBuilder,
                                  @Value("${app.auth-service.url}") String authServiceUrl,
                                  @Value("${app.summary-service.url}") String summaryServiceUrl) {
        this.clientBuilder = clientBuilder;
        this.authServiceUrl = trimTrailingSlash(authServiceUrl);
        this.summaryServiceUrl = trimTrailingSlash(summaryServiceUrl);
    }

    @RequestMapping("/gateway/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
                                        @RequestBody(required = false) byte[] body) throws IOException {
        String forwardedPath = request.getRequestURI().substring(PREFIX.length());
        int separator = forwardedPath.indexOf('/');
        if (separator < 1) {
            return ResponseEntity.notFound().build();
        }
        String service = forwardedPath.substring(0, separator);
        String baseUrl = switch (service) {
            case "auth" -> authServiceUrl;
            case "summary" -> summaryServiceUrl;
            default -> null;
        };
        if (baseUrl == null) {
            return ResponseEntity.notFound().build();
        }

        String target = baseUrl + forwardedPath.substring(separator);
        if (request.getQueryString() != null) {
            target += "?" + request.getQueryString();
        }
        RestClient.RequestBodySpec outbound = clientBuilder.build()
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(URI.create(target))
                .headers(headers -> FORWARDED_HEADERS.forEach(name -> {
                    String value = request.getHeader(name);
                    if (value != null) {
                        headers.set(name, value);
                    }
                }));
        if (body != null && body.length > 0) {
            outbound.body(body);
        }

        return outbound.exchange((clientRequest, clientResponse) -> {
            HttpHeaders responseHeaders = new HttpHeaders();
            if (clientResponse.getHeaders().getContentType() != null) {
                responseHeaders.setContentType(clientResponse.getHeaders().getContentType());
            }
            return ResponseEntity.status(clientResponse.getStatusCode())
                    .headers(responseHeaders)
                    .body(StreamUtils.copyToByteArray(clientResponse.getBody()));
        });
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
