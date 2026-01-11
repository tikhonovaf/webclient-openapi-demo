package ru.webclientpersonaccount.controller;

import ru.webclientpersonaccount.server.api.AggregateApi;
import ru.webclientpersonaccount.server.model.AggregatedInfo;
import ru.webclientpersonaccount.service.PersonAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class PersonAccountController implements AggregateApi {

    private final PersonAccountService personAccountService;

    public PersonAccountController(PersonAccountService personAccountService) {
        this.personAccountService = personAccountService;
    }

    @Override
    public Mono<ResponseEntity<AggregatedInfo>> aggregatePersonAndAccount(
            Long personId, Long accountId, ServerWebExchange exchange) {
        return personAccountService.getAggregatedData(personId, accountId)
                .map(ResponseEntity::ok);
//                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}