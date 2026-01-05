package ru.webclientpetstore;


import ru.webclientpetstore.service.PetStoreService;
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
class PetStoreIntegrationTest {

    @Autowired
    private PetStoreService petStoreService;

    // Регистрируем WireMock сервер на динамическом порту
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    // Перехватываем настройки из application.yml и подменяем URL на WireMock
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("services.pet.url", wireMock::baseUrl);
        registry.add("services.store.url", wireMock::baseUrl);
    }

    @Test
    @DisplayName("Успешная агрегация данных из Pet и Store сервисов")
    void shouldReturnAggregatedDataSuccessfully() {
        // Настройка стаба для Pet API
        wireMock.stubFor(get(urlEqualTo("/pet/1"))
                .willReturn(okJson("""
                        {
                          "id": 1,
                          "name": "Doggo",
                          "status": "available"
                        }
                        """)));

        // Настройка стаба для Store API
        wireMock.stubFor(get(urlEqualTo("/store/1"))
                .willReturn(okJson("""
                        {
                          "id": 1,
                          "name": "Central Pet Shop",
                          "status": "open"
                        }
                        """)));

        // Тестирование реактивного метода сервиса
        StepVerifier.create(petStoreService.getAggregatedData(1L, 1L))
                .expectNextMatches(info ->
                        "Doggo".equals(info.getPetName()) &&
                                "open".equals(info.getStoreStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Проверка работы Retry: успех после одной ошибки 500")
    void shouldRetryOnExternalErrorAndSucceed() {
        String scenario = "Retry Scenario";

        // 1-й вызов Pet API вернет 500
        wireMock.stubFor(get(urlEqualTo("/pet/1"))
                .inScenario(scenario)
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("Success State"));

        // 2-й вызов Pet API (Retry) вернет 200
        wireMock.stubFor(get(urlEqualTo("/pet/1"))
                .inScenario(scenario)
                .whenScenarioStateIs("Success State")
                .willReturn(okJson("{\"id\": 1, \"name\": \"Surviving Doggo\"}")));

        // Store API всегда отвечает 200
        wireMock.stubFor(get(urlEqualTo("/store/1"))
                .willReturn(okJson("{\"id\": 1, \"status\": \"open\"}")));

        StepVerifier.create(petStoreService.getAggregatedData(1L, 1L))
                .expectNextMatches(info -> "Surviving Doggo".equals(info.getPetName()))
                .verifyComplete();

        // Проверяем, что было действительно 2 запроса к Pet API
        wireMock.verify(2, getRequestedFor(urlEqualTo("/pet/1")));
    }

    @Test
    @DisplayName("Проверка Fallback: возврат дефолтных данных при полной недоступности")
    void shouldReturnFallbackWhenServicesAreDown() {
        // Настраиваем вечную ошибку 500 для Pet API
        wireMock.stubFor(get(urlPathMatching("/pet/.*"))
                .willReturn(aResponse().withStatus(500)));

        // После исчерпания Retry должен сработать Fallback метод
        StepVerifier.create(petStoreService.getAggregatedData(1L, 1L))
                .expectNextMatches(info ->
                        "Unknown (Service Unavailable)".equals(info.getPetName()) &&
                                "N/A".equals(info.getStoreStatus()))
                .verifyComplete();
    }
}