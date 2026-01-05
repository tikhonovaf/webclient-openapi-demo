package ru.webclientpetstore.controller;

import ru.webclientpetstore.server.api.AggregateApi;
import ru.webclientpetstore.server.model.AggregatedInfo;
import ru.webclientpetstore.service.PetStoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class PetStoreController implements AggregateApi {

    private final PetStoreService petStoreService;

    public PetStoreController(PetStoreService petStoreService) {
        this.petStoreService = petStoreService;
    }

    @Override
    public Mono<ResponseEntity<AggregatedInfo>> aggregatePetAndStore(
            Long petId, Long storeId, ServerWebExchange exchange) {
        return petStoreService.getAggregatedData(petId, storeId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}