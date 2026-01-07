package ru.webclientpetstore.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean; // НОВЫЙ ИМПОРТ
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.webclientpetstore.server.model.AggregatedInfo;
import ru.webclientpetstore.service.PetStoreService;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = PetStoreController.class)
class PetStoreControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private PetStoreService petStoreService;

    @Test
    @DisplayName("GET /aggregate/{petId}/{storeId} должен возвращать 200 и JSON")
    void shouldGetInfoSuccess() {
        // Arrange
        AggregatedInfo mockInfo = new AggregatedInfo();
        mockInfo.setPetName("Rex");
        mockInfo.setStoreStatus("available");

        when(petStoreService.getAggregatedData(1L, 10L))
                .thenReturn(Mono.just(mockInfo));

        // Act & Assert
        webTestClient.get()
                .uri("/aggregate/1/10") // Проверьте, что путь совпадает с вашим YAML
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.petName").isEqualTo("Rex")
                .jsonPath("$.storeStatus").isEqualTo("available");
    }

    @Test
    @DisplayName("Должен возвращать данные даже если сервис ответил Fallback-объектом")
    void shouldReturnFallbackData() {
        // Arrange
        AggregatedInfo fallback = new AggregatedInfo();
        fallback.setPetName("Unknown (Service Unavailable)");
        fallback.setStoreStatus("N/A");

        when(petStoreService.getAggregatedData(anyLong(), anyLong()))
                .thenReturn(Mono.just(fallback));

        // Act & Assert
        webTestClient.get()
                .uri("/aggregate/999/999")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.petName").isEqualTo("Unknown (Service Unavailable)")
                .jsonPath("$.storeStatus").isEqualTo("N/A");
    }
}