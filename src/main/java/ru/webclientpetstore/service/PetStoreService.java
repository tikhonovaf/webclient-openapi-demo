package ru.webclientpetstore.service;


import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.webclientpetstore.client.pet.api.PetApi;
import ru.webclientpetstore.client.store.api.StoreApi;
import ru.webclientpetstore.server.model.AggregatedInfo;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
public class PetStoreService {

    private final PetApi petApi;
    private final StoreApi storeApi;

    public PetStoreService(PetApi petApi, StoreApi storeApi) {
        this.petApi = petApi;
        this.storeApi = storeApi;
    }

    public Mono<AggregatedInfo> getAggregatedData(Long petId, Long storeId) {
        return Mono.zip(
                        // Плечо PET с логикой повторов
                        petApi.getPetById(petId)
                                .timeout(Duration.ofSeconds(3))
                                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1))
                                        .filter(ex -> ex instanceof WebClientResponseException.InternalServerError ||
                                                ex instanceof java.util.concurrent.TimeoutException)),

                        // Плечо STORE с логикой повторов
                        storeApi.getStoreById(storeId)
                                .timeout(Duration.ofSeconds(3))
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
                    System.err.println("Final error after retries: " + e.getMessage());
                    AggregatedInfo fallback = new AggregatedInfo();
                    fallback.setPetName("Unknown (Service Unavailable)");
                    fallback.setStoreStatus("N/A");
                    return Mono.just(fallback);
                });
    }
}