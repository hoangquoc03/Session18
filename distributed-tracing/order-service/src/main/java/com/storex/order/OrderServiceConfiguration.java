package com.storex.order;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class OrderServiceConfiguration {

    @Bean
    RestClient userRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.user.base-url}") String userBaseUrl,
            @Value("${services.user.timeout:3s}") Duration timeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return restClientBuilder
                .baseUrl(userBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
