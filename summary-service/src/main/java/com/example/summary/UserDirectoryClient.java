package com.example.summary;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class UserDirectoryClient {
    private final RestClient client;

    public UserDirectoryClient(@Value("${app.auth-service.url}") String authServiceUrl) {
        client = RestClient.builder().baseUrl(authServiceUrl).build();
    }

    public List<LendBorrowDtos.UserView> users(String bearerToken) {
        LendBorrowDtos.UserView[] users = client.get().uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(LendBorrowDtos.UserView[].class);
        return users == null ? List.of() : List.of(users);
    }
}