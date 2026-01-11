package ru.webclientpersonaccount.service;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import ru.webclientpersonaccount.client.person.api.PersonApi;
import ru.webclientpersonaccount.client.person.model.Person;
import ru.webclientpersonaccount.client.account.api.AccountApi;
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
class PersonAccountServiceTest {

    @Mock
    private PersonApi personApi;

    @Mock
    private AccountApi accountApi;

    // Используем реальный реестр в памяти вместо мока
    private MeterRegistry meterRegistry;

    // Нам нужно проинициализировать мок счетчика,
    // так как сервис вызывает .counter() в конструкторе
    @Mock
    private Counter fallbackCounter;

    // Убираем @InjectMocks, будем создавать вручную
    private PersonAccountService personAccountService;

    @BeforeEach
    void setUp() {
        // Создаем чистый реестр перед каждым тестом
        meterRegistry = new SimpleMeterRegistry();

        // Вручную создаем экземпляр сервиса
        personAccountService = new PersonAccountService(personApi, accountApi, meterRegistry);
    }

    @Test
    @DisplayName("Успешное объединение данных при корректном ответе обоих API")
    void shouldReturnAggregatedDataWhenBothApisSucceed() {
        // Создаем моки ответов.
        // Вместо конкретных классов используем mock() для Account, раз его имя неизвестно
        Person mockPerson = new Person();
        mockPerson.setName("Barsik");

        // Имитируем объект из AccountApi, у которого есть getStatus().getValue()
        var mockAccountResponse = mock(ru.webclientpersonaccount.client.account.model.Account.class, RETURNS_DEEP_STUBS);
        when(mockAccountResponse.getStatus().getValue()).thenReturn("available");

        when(personApi.getPersonById(1L)).thenReturn(Mono.just(mockPerson));
        when(accountApi.getAccountById(2L)).thenReturn(Mono.just(mockAccountResponse));

        StepVerifier.create(personAccountService.getAggregatedData(1L, 2L))
                .expectNextMatches(info ->
                        "Barsik".equals(info.getPersonName()) &&
                                "available".equals(info.getAccountStatus()))
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked") // Подавляем предупреждение о generics
    @DisplayName("Проверка Retry: Успех после одной ошибки 500")
    void shouldRetryOnInternalServerErrorAndSucceed() {
        Person mockPerson = new Person();
        mockPerson.setName("RetryPerson");

        var mockAccountResponse = mock(ru.webclientpersonaccount.client.account.model.Account.class, RETURNS_DEEP_STUBS);
        when(mockAccountResponse.getStatus().getValue()).thenReturn("ok");

        when(personApi.getPersonById(1L))
                .thenReturn(Mono.error(WebClientResponseException.create(500, "Error", null, null, null)))
                .thenReturn(Mono.just(mockPerson));

        when(accountApi.getAccountById(anyLong())).thenReturn(Mono.just(mockAccountResponse));

        // Используем withVirtualTime, чтобы "промотать" 1 секунду задержки ретрая
        StepVerifier.withVirtualTime(() -> personAccountService.getAggregatedData(1L, 2L))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(2)) // Ждем виртуальные 2 секунды (хватит для ретрая)
                .expectNextMatches(info -> "RetryPerson".equals(info.getPersonName()))
                .verifyComplete();

        verify(personApi, times(2)).getPersonById(1L);
    }

    @Test
    @DisplayName("Fallback: возврат дефолтных данных после всех неудачных попыток")
    void shouldReturnFallbackAfterAllRetriesFailed() {
        // Всегда возвращаем ошибку, которая должна триггерить Retry, а затем Fallback
        when(personApi.getPersonById(anyLong())).thenReturn(Mono.error(new TimeoutException("Timeout!")));

        // Для Account просто возвращаем что угодно
        var mockAccountResponse = mock(ru.webclientpersonaccount.client.account.model.Account.class, RETURNS_DEEP_STUBS);
        when(accountApi.getAccountById(anyLong())).thenReturn(Mono.just(mockAccountResponse));

        StepVerifier.create(personAccountService.getAggregatedData(1L, 1L))
                .expectNextMatches(info ->
                        "Unknown (Service Unavailable)".equals(info.getPersonName()) &&
                                "N/A".equals(info.getAccountStatus()))
                .verifyComplete();
    }
}