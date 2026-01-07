package ru.webclientpetstore.service;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import ru.webclientpetstore.client.pet.api.PetApi;
import ru.webclientpetstore.client.pet.model.Pet;
import ru.webclientpetstore.client.store.api.StoreApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.junit.jupiter.api.DisplayName;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class PetStoreServiceTest {

    @Mock
    private PetApi petApi;

    @Mock
    private StoreApi storeApi;

    // Используем реальный реестр в памяти вместо мока
    private MeterRegistry meterRegistry;

    // Нам нужно проинициализировать мок счетчика,
    // так как сервис вызывает .counter() в конструкторе
    @Mock
    private Counter fallbackCounter;

    // Убираем @InjectMocks, будем создавать вручную
    private PetStoreService petStoreService;

    @BeforeEach
    void setUp() {
        // Создаем чистый реестр перед каждым тестом
        meterRegistry = new SimpleMeterRegistry();

        // Вручную создаем экземпляр сервиса
        petStoreService = new PetStoreService(petApi, storeApi, meterRegistry);
    }

    @Test
    @DisplayName("Успешное объединение данных при корректном ответе обоих API")
    void shouldReturnAggregatedDataWhenBothApisSucceed() {
        // Создаем моки ответов.
        // Вместо конкретных классов используем mock() для Store, раз его имя неизвестно
        Pet mockPet = new Pet();
        mockPet.setName("Barsik");

        // Имитируем объект из StoreApi, у которого есть getStatus().getValue()
        var mockStoreResponse = mock(ru.webclientpetstore.client.store.model.Store.class, RETURNS_DEEP_STUBS);
        when(mockStoreResponse.getStatus().getValue()).thenReturn("available");

        when(petApi.getPetById(1L)).thenReturn(Mono.just(mockPet));
        when(storeApi.getStoreById(2L)).thenReturn(Mono.just(mockStoreResponse));

        StepVerifier.create(petStoreService.getAggregatedData(1L, 2L))
                .expectNextMatches(info ->
                        "Barsik".equals(info.getPetName()) &&
                                "available".equals(info.getStoreStatus()))
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked") // Подавляем предупреждение о generics
    @DisplayName("Проверка Retry: Успех после одной ошибки 500")
    void shouldRetryOnInternalServerErrorAndSucceed() {
        Pet mockPet = new Pet();
        mockPet.setName("RetryPet");

        var mockStoreResponse = mock(ru.webclientpetstore.client.store.model.Store.class, RETURNS_DEEP_STUBS);
        when(mockStoreResponse.getStatus().getValue()).thenReturn("ok");

        when(petApi.getPetById(1L))
                .thenReturn(Mono.error(WebClientResponseException.create(500, "Error", null, null, null)))
                .thenReturn(Mono.just(mockPet));

        when(storeApi.getStoreById(anyLong())).thenReturn(Mono.just(mockStoreResponse));

        // Используем withVirtualTime, чтобы "промотать" 1 секунду задержки ретрая
        StepVerifier.withVirtualTime(() -> petStoreService.getAggregatedData(1L, 2L))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(2)) // Ждем виртуальные 2 секунды (хватит для ретрая)
                .expectNextMatches(info -> "RetryPet".equals(info.getPetName()))
                .verifyComplete();

        verify(petApi, times(2)).getPetById(1L);
    }

    @Test
    @DisplayName("Fallback: возврат дефолтных данных после всех неудачных попыток")
    void shouldReturnFallbackAfterAllRetriesFailed() {
        // Всегда возвращаем ошибку, которая должна триггерить Retry, а затем Fallback
        when(petApi.getPetById(anyLong())).thenReturn(Mono.error(new TimeoutException("Timeout!")));

        // Для Store просто возвращаем что угодно
        var mockStoreResponse = mock(ru.webclientpetstore.client.store.model.Store.class, RETURNS_DEEP_STUBS);
        when(storeApi.getStoreById(anyLong())).thenReturn(Mono.just(mockStoreResponse));

        StepVerifier.create(petStoreService.getAggregatedData(1L, 1L))
                .expectNextMatches(info ->
                        "Unknown (Service Unavailable)".equals(info.getPetName()) &&
                                "N/A".equals(info.getStoreStatus()))
                .verifyComplete();
    }
}