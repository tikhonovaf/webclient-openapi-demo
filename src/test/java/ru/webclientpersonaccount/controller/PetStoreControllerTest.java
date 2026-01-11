package ru.webclientpersonaccount.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean; // НОВЫЙ ИМПОРТ
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.webclientpersonaccount.server.model.AggregatedInfo;
import ru.webclientpersonaccount.service.PersonAccountService;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = PersonAccountController.class)
class PersonAccountControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private PersonAccountService personAccountService;

    @Test
    @DisplayName("GET /aggregate/{personId}/{accountId} должен возвращать 200 и JSON")
    void shouldGetInfoSuccess() {
        // Arrange
        AggregatedInfo mockInfo = new AggregatedInfo();
        mockInfo.setPersonName("Rex");
        mockInfo.setAccountStatus("available");

        when(personAccountService.getAggregatedData(1L, 10L))
                .thenReturn(Mono.just(mockInfo));

        // Act & Assert
        webTestClient.get()
                .uri("/aggregate/1/10") // Проверьте, что путь совпадает с вашим YAML
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.personName").isEqualTo("Rex")
                .jsonPath("$.accountStatus").isEqualTo("available");
    }

    @Test
    @DisplayName("Должен возвращать данные даже если сервис ответил Fallback-объектом")
    void shouldReturnFallbackData() {
        // Arrange
        AggregatedInfo fallback = new AggregatedInfo();
        fallback.setPersonName("Unknown (Service Unavailable)");
        fallback.setAccountStatus("N/A");

        when(personAccountService.getAggregatedData(anyLong(), anyLong()))
                .thenReturn(Mono.just(fallback));

        // Act & Assert
        webTestClient.get()
                .uri("/aggregate/999/999")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.personName").isEqualTo("Unknown (Service Unavailable)")
                .jsonPath("$.accountStatus").isEqualTo("N/A");
    }
}