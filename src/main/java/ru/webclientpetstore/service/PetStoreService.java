package ru.webclientpetstore.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.webclientpetstore.client.pet.api.PetApi;
import ru.webclientpetstore.client.store.api.StoreApi;
import ru.webclientpetstore.exception.EntityNotFoundException;
import ru.webclientpetstore.server.model.AggregatedInfo;
import reactor.util.retry.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;

@Slf4j
@Service
public class PetStoreService {

    private final PetApi petApi;
    private final StoreApi storeApi;
    private final Counter fallbackCounter;

    public PetStoreService(PetApi petApi, StoreApi storeApi, MeterRegistry registry) {
        this.petApi = petApi;
        this.storeApi = storeApi;
        // Создаем и регистрируем счетчик с понятным названием
        this.fallbackCounter = Counter.builder("petstore.aggregation.fallback")
                .description("Количество срабатываний fallback-логики")
                .tag("type", "total")
                .register(registry);
    }

    public Mono<AggregatedInfo> getAggregatedData(Long petId, Long storeId) {
        return Mono.zip(
                        // Плечо PET с логикой повторов
                        // Используем defer, чтобы каждый retry создавал новый холодный поток
                        Mono.defer(() -> petApi.getPetById(petId))
                                .timeout(Duration.ofSeconds(3))
                                // Если пришел 404 — превращаем в наше исключение
                                .onErrorMap(WebClientResponseException.NotFound.class,
                                        e -> new EntityNotFoundException("Питомец с ID " + petId + " не найден"))
                                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1))
                                        .filter(ex -> ex instanceof WebClientResponseException.InternalServerError ||
                                                ex instanceof java.util.concurrent.TimeoutException)),

                        // Плечо STORE с логикой повторов
                        Mono.defer(() -> storeApi.getStoreById(storeId))
                                .timeout(Duration.ofSeconds(3))
                                .onErrorMap(WebClientResponseException.NotFound.class,
                                        e -> new EntityNotFoundException("Заказ с ID " + storeId + " не найден"))
                                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
                )
                .map(tuple -> {
                    AggregatedInfo info = new AggregatedInfo();
                    info.setPetName(tuple.getT1().getName());
                    info.setStoreStatus(tuple.getT2().getStatus().getValue());
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
                    fallback.setPetName("Unknown (Service Unavailable)");
                    fallback.setStoreStatus("N/A");
                    return Mono.just(fallback);
                });
    }
}