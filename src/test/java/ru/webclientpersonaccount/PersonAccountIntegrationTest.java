package ru.webclientpersonaccount;


import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.webclientpersonaccount.service.PersonAccountService;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PersonAccountIntegrationTest {

    @Autowired
    private PersonAccountService personAccountService;

    @Autowired
    private WebTestClient webTestClient;

    // Регистрируем WireMock сервер на динамическом порту
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    // Перехватываем настройки из application.yml и подменяем URL на WireMock
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("services.person.url", wireMock::baseUrl);
        registry.add("services.account.url", wireMock::baseUrl);
    }

    @Test
    @DisplayName("Успешная агрегация данных из Person и Account сервисов")
    void shouldReturnAggregatedDataSuccessfully() {
        // Настройка стаба для Person API
        wireMock.stubFor(get(urlEqualTo("/person/1"))
                .willReturn(okJson("""
                        {
                          "id": 1,
                          "name": "Doggo",
                          "status": "active"
                        }
                        """)));

        // Настройка стаба для Account API
        wireMock.stubFor(get(urlEqualTo("/account/1"))
                .willReturn(okJson("""
                        {
                          "id": 1,
                          "name": "Central Person Shop",
                          "status": "open"
                        }
                        """)));

        // Тестирование реактивного метода сервиса
        StepVerifier.create(personAccountService.getAggregatedData(1L, 1L))
                .expectNextMatches(info ->
                        "Doggo".equals(info.getPersonName()) &&
                                "open".equals(info.getAccountStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Проверка работы Retry: успех после одной ошибки 500")
    void shouldRetryOnExternalErrorAndSucceed() {
        String scenario = "Retry Scenario";

        // 1-й вызов Person API вернет 500
        wireMock.stubFor(get(urlEqualTo("/person/1"))
                .inScenario(scenario)
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("Success State"));

        // 2-й вызов Person API (Retry) вернет 200
        wireMock.stubFor(get(urlEqualTo("/person/1"))
                .inScenario(scenario)
                .whenScenarioStateIs("Success State")
                .willReturn(okJson("""
                        {
                          "id": 1,
                          "name": "Surviving Doggo"
                        }
                        """)));

        // Account API всегда отвечает 200
        wireMock.stubFor(get(urlEqualTo("/account/1"))
                .willReturn(okJson("""
                        {
                          "id": 1,
                          "status": "open"
                        }
                        """)));
        StepVerifier.create(personAccountService.getAggregatedData(1L, 1L))
                .expectNextMatches(info -> "Surviving Doggo".equals(info.getPersonName()))
                .verifyComplete();

        // Проверяем, что было действительно 2 запроса к Person API
        wireMock.verify(2, getRequestedFor(urlEqualTo("/person/1")));
    }

    @Test
    @DisplayName("Проверка Fallback: возврат дефолтных данных при полной недоступности")
    void shouldReturnFallbackWhenServicesAreDown() {
        // Настраиваем вечную ошибку 500 для Person API
        wireMock.stubFor(get(urlPathMatching("/person/.*"))
                .willReturn(aResponse().withStatus(500)));

        // После исчерпания Retry должен сработать Fallback метод
        StepVerifier.create(personAccountService.getAggregatedData(1L, 1L))
                .expectNextMatches(info ->
                        "Unknown (Service Unavailable)".equals(info.getPersonName()) &&
                                "N/A".equals(info.getAccountStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Интеграция: внешнее API возвращает 404 -> Наш контроллер возвращает ProblemDetail 404")
    void shouldReturn404ProblemDetailWhenExternalServiceReturns404() {
        // 1. Настраиваем WireMock на возврат 404 для конкретного ID
        wireMock.stubFor(get(urlEqualTo("/person/404"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\": \"Person not found in external system\"}")));

        // Account API пусть отвечает успешно
        wireMock.stubFor(get(urlPathMatching("/account/.*"))
                .willReturn(okJson("{\"id\": 1, \"status\": \"open\"}")));

        // 2. Вызываем наш КОНТРОЛЛЕР (не сервис напрямую!)
        webTestClient.get()
                .uri("/aggregate/404/1")
                .exchange()
                // 3. Проверяем, что GlobalExceptionHandler отработал
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.title").isEqualTo("Сущность не найдена")
                .jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("404"))
                .jsonPath("$.instance").isEqualTo("/aggregate/404/1");
    }
}