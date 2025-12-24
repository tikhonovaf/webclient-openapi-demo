package ru.webclientopenapidemo;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;
import ru.webclientopenapidemo.service.PetService;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;

@SpringBootTest
class PetIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private PetService petService;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @Test
    void testGetPet() {
        // Имитируем ответ сервера
        wireMockServer.stubFor(get(urlEqualTo("/pets/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 1, \"name\": \"Doggo\"}")));

        // Используем StepVerifier для проверки реактивного потока
        StepVerifier.create(petService.fetchPet(1L))
                .expectNextMatches(pet -> pet.getName().equals("Doggo"))
                .verifyComplete();
    }
}