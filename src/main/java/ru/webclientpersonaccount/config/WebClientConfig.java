package ru.webclientpersonaccount.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import ru.webclientpersonaccount.client.person.api.PersonApi;
import ru.webclientpersonaccount.client.account.api.AccountApi;

@Configuration
public class WebClientConfig {

    // --- Блок для Person API ---

    @Bean
    public ru.webclientpersonaccount.client.person.ApiClient personApiClient(
            WebClient.Builder builder,
            @Value("${services.person.url}") String personUrl) {

        // Создаем экземпляр клиента для Person Service
        ru.webclientpersonaccount.client.person.ApiClient apiClient =
                new ru.webclientpersonaccount.client.person.ApiClient(builder.build());
        apiClient.setBasePath(personUrl);
        return apiClient;
    }

    @Bean
    public PersonApi personApi(ru.webclientpersonaccount.client.person.ApiClient personApiClient) {
        return new PersonApi(personApiClient);
    }

    // --- Блок для ACCOUNT API ---

    @Bean
    public ru.webclientpersonaccount.client.account.ApiClient accountApiClient(
            WebClient.Builder builder,
            @Value("${services.account.url}") String accountUrl) {

        // Создаем экземпляр клиента для Account Service
        ru.webclientpersonaccount.client.account.ApiClient apiClient =
                new ru.webclientpersonaccount.client.account.ApiClient(builder.build());
        apiClient.setBasePath(accountUrl);
        return apiClient;
    }

    @Bean
    public AccountApi accountApi(ru.webclientpersonaccount.client.account.ApiClient accountApiClient) {
        return new AccountApi(accountApiClient);
    }
}