package ru.webclientpersonaccount.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.webclientpersonaccount.client.person.api.PersonApi;
import ru.webclientpersonaccount.client.account.api.AccountApi;
import ru.webclientpersonaccount.exception.EntityNotFoundException;
import ru.webclientpersonaccount.server.model.AggregatedInfo;
import reactor.util.retry.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;

@Slf4j
@Service
public class PersonAccountService {

    private final PersonApi personApi;
    private final AccountApi accountApi;
    private final Counter fallbackCounter;

    public PersonAccountService(PersonApi personApi, AccountApi accountApi, MeterRegistry registry) {
        this.personApi = personApi;
        this.accountApi = accountApi;
        // Создаем и регистрируем счетчик с понятным названием
        this.fallbackCounter = Counter.builder("personaccount.aggregation.fallback")
                .description("Количество срабатываний fallback-логики")
                .tag("type", "total")
                .register(registry);
    }

    public Mono<AggregatedInfo> getAggregatedData(Long personId, Long accountId) {
        return Mono.zip(
                        // Плечо Person с логикой повторов
                        // Используем defer, чтобы каждый retry создавал новый холодный поток
                        Mono.defer(() -> personApi.getPersonById(personId))
                                .timeout(Duration.ofSeconds(3))
                                // Если пришел 404 — превращаем в наше исключение
                                .onErrorMap(WebClientResponseException.NotFound.class,
                                        e -> new EntityNotFoundException("Клиент с ID " + personId + " не найден"))
                                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1))
                                        .filter(ex -> ex instanceof WebClientResponseException.InternalServerError ||
                                                ex instanceof java.util.concurrent.TimeoutException)),

                        // Плечо ACCOUNT с логикой повторов
                        Mono.defer(() -> accountApi.getAccountById(accountId))
                                .timeout(Duration.ofSeconds(3))
                                .onErrorMap(WebClientResponseException.NotFound.class,
                                        e -> new EntityNotFoundException("Заказ с ID " + accountId + " не найден"))
                                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
                )
                .map(tuple -> {
                    AggregatedInfo info = new AggregatedInfo();
                    info.setPersonName(tuple.getT1().getName());
                    info.setAccountStatus(tuple.getT2().getStatus().getValue());
                    return info;
                })
                // Если после всех попыток всё еще ошибка — идем в Fallback
                .onErrorResume(e -> {
                    // КРИТИЧЕСКИЙ МОМЕНТ: если это наше исключение (404), пробрасываем его дальше!
                    // Мы не хотим заменять его на "Unknown (Service Unavailable)"
                    if (e instanceof EntityNotFoundException) {
                        return Mono.error(e);
                    }

                    log.error("Final technical error after retries: {}", e.getMessage());
                    fallbackCounter.increment();

                    AggregatedInfo fallback = new AggregatedInfo();
                    fallback.setPersonName("Unknown (Service Unavailable)");
                    fallback.setAccountStatus("N/A");
                    return Mono.just(fallback);
                });
    }
}