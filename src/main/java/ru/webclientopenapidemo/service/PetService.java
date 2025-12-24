package ru.webclientopenapidemo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.example.api.PetApi;
import com.example.model.Pet;

@Service
@RequiredArgsConstructor // Lombok сам создаст конструктор для всех final-полей
public class PetService {

    private final PetApi petApi;

    public Mono<Pet> fetchPet(Long id) {
        return petApi.getPetById(id);
    }
}