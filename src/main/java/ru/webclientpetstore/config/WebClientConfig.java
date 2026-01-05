package ru.webclientpetstore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import ru.webclientpetstore.client.pet.api.PetApi;
import ru.webclientpetstore.client.store.api.StoreApi;

@Configuration
public class WebClientConfig {

    // --- Блок для PET API ---

    @Bean
    public ru.webclientpetstore.client.pet.ApiClient petApiClient(
            WebClient.Builder builder,
            @Value("${services.pet.url}") String petUrl) {

        // Создаем экземпляр клиента для Pet Service
        ru.webclientpetstore.client.pet.ApiClient apiClient =
                new ru.webclientpetstore.client.pet.ApiClient(builder.build());
        apiClient.setBasePath(petUrl);
        return apiClient;
    }

    @Bean
    public PetApi petApi(ru.webclientpetstore.client.pet.ApiClient petApiClient) {
        return new PetApi(petApiClient);
    }

    // --- Блок для STORE API ---

    @Bean
    public ru.webclientpetstore.client.store.ApiClient storeApiClient(
            WebClient.Builder builder,
            @Value("${services.store.url}") String storeUrl) {

        // Создаем экземпляр клиента для Store Service
        ru.webclientpetstore.client.store.ApiClient apiClient =
                new ru.webclientpetstore.client.store.ApiClient(builder.build());
        apiClient.setBasePath(storeUrl);
        return apiClient;
    }

    @Bean
    public StoreApi storeApi(ru.webclientpetstore.client.store.ApiClient storeApiClient) {
        return new StoreApi(storeApiClient);
    }
}