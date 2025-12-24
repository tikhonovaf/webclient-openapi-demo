package ru.webclientopenapidemo.config;

import com.example.ApiClient;
import com.example.api.PetApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ClientConfiguration {

    @Bean
    public PetApi petApi(WebClient.Builder builder) {
        // Указываем базовый URL внешнего сервиса
        ApiClient apiClient = new ApiClient(builder.baseUrl("http://localhost:8089").build());
        return new PetApi(apiClient);
    }
}
