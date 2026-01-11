package ru.webclientpersonaccount.controller;

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
import ru.webclientpersonaccount.exception.EntityNotFoundException;
import ru.webclientpersonaccount.exception.GlobalExceptionHandler;
import ru.webclientpersonaccount.service.PersonAccountService;

import static org.hamcrest.Matchers.containsString;

@WebFluxTest(controllers = PersonAccountController.class)
@Import(GlobalExceptionHandler.class)
class PersonAccountValidationTest {

    @Autowired
    private WebTestClient webTestClient;

    // Вместо @MockBean теперь используем это:
    @MockitoBean
    private PersonAccountService personAccountService;

//    @Test
//    @DisplayName("Должен вернуть 400, если personId меньше 1")
//    void shouldReturn400WhenPersonIdIsInvalid() {
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
    @DisplayName("Должен вернуть 400 и ProblemDetail при personId = 0")
    void shouldReturn400WhenPersonIdIsInvalid() {
        // Arrange
        Long invalidPersonId = 0L;
        Long accountId = 1L;

        // Act & Assert
        webTestClient.get()
                .uri("/aggregate/{personId}/{accountId}", invalidPersonId, accountId)
                .exchange()
                // 1. Проверяем статус
                .expectStatus().isBadRequest()
                // 2. Проверяем Header (стандарт для ProblemDetail)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                // 3. Проверяем содержимое JSON
                .expectBody()
                .jsonPath("$.title").isEqualTo("Validation Failed") // Из вашего нового метода
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.detail").value(containsString("personId"))
                .jsonPath("$.detail").value(containsString("должно быть не меньше 1"))
                .jsonPath("$.timestamp").exists();
    }

    @Test
    @DisplayName("Должен вернуть 404, если сервис не нашел клиента")
    void shouldReturn404WhenPersonNotFound() {
        // Arrange
        Long personId = 99L;
        Long accountId = 1L;

        // Настраиваем мок: имитируем ситуацию, когда данные не найдены
        Mockito.when(personAccountService.getAggregatedData(personId, accountId))
                .thenReturn(Mono.error(new EntityNotFoundException("Клиент с ID " + personId + " не найден")));

        // Act & Assert
        webTestClient.get()
                .uri("/aggregate/{personId}/{accountId}", personId, accountId)
                .exchange()
                // 1. Ожидаем статус 404 NOT FOUND
                .expectStatus().isNotFound()
                // 2. Проверяем формат ProblemDetail
                .expectBody()
                .jsonPath("$.title").isEqualTo("Сущность не найдена") // Заголовок из вашего обработчика
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").isEqualTo("Клиент с ID 99 не найден")
                .jsonPath("$.instance").isEqualTo("/aggregate/99/1"); // Если вы добавили setInstance в обработчик
    }
}