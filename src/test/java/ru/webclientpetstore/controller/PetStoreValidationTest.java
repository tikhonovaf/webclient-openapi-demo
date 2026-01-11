package ru.webclientpetstore.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.webclientpetstore.exception.EntityNotFoundException;
import ru.webclientpetstore.exception.GlobalExceptionHandler;
import ru.webclientpetstore.service.PetStoreService;

import static org.hamcrest.Matchers.containsString;

@WebFluxTest(controllers = PetStoreController.class)
@Import(GlobalExceptionHandler.class)
class PetStoreValidationTest {

    @Autowired
    private WebTestClient webTestClient;

    // Вместо @MockBean теперь используем это:
    @MockitoBean
    private PetStoreService petStoreService;

//    @Test
//    @DisplayName("Должен вернуть 400, если petId меньше 1")
//    void shouldReturn400WhenPetIdIsInvalid() {
//        webTestClient.get()
//                .uri("/aggregate/0/1")
//                .exchange()
//                .expectBody()
//                .consumeWith(result -> {
//                    String body = new String(result.getResponseBodyContent());
//                    System.out.println("ACTUAL RESPONSE BODY: " + body);
//                });
//    }

    @Test
    @DisplayName("Должен вернуть 400 и ProblemDetail при petId = 0")
    void shouldReturn400WhenPetIdIsInvalid() {
        // Arrange
        Long invalidPetId = 0L;
        Long storeId = 1L;

        // Act & Assert
        webTestClient.get()
                .uri("/aggregate/{petId}/{storeId}", invalidPetId, storeId)
                .exchange()
                // 1. Проверяем статус
                .expectStatus().isBadRequest()
                // 2. Проверяем Header (стандарт для ProblemDetail)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                // 3. Проверяем содержимое JSON
                .expectBody()
                .jsonPath("$.title").isEqualTo("Validation Failed") // Из вашего нового метода
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.detail").value(containsString("petId"))
                .jsonPath("$.detail").value(containsString("должно быть не меньше 1"))
                .jsonPath("$.timestamp").exists();
    }

    @Test
    @DisplayName("Должен вернуть 404, если сервис не нашел питомца")
    void shouldReturn404WhenPetNotFound() {
        // Arrange
        Long petId = 99L;
        Long storeId = 1L;

        // Настраиваем мок: имитируем ситуацию, когда данные не найдены
        Mockito.when(petStoreService.getAggregatedData(petId, storeId))
                .thenReturn(Mono.error(new EntityNotFoundException("Питомец с ID " + petId + " не найден")));

        // Act & Assert
        webTestClient.get()
                .uri("/aggregate/{petId}/{storeId}", petId, storeId)
                .exchange()
                // 1. Ожидаем статус 404 NOT FOUND
                .expectStatus().isNotFound()
                // 2. Проверяем формат ProblemDetail
                .expectBody()
                .jsonPath("$.title").isEqualTo("Сущность не найдена") // Заголовок из вашего обработчика
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").isEqualTo("Питомец с ID 99 не найден")
                .jsonPath("$.instance").isEqualTo("/aggregate/99/1"); // Если вы добавили setInstance в обработчик
    }
}