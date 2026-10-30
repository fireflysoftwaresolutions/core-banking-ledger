/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.core.banking.ledger.core.services.money;
import io.fireflysoftware.core.banking.ledger.core.services.money.impl.MoneyServiceImpl;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import io.fireflysoftware.core.banking.ledger.core.mappers.money.MoneyMapper;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.money.MoneyDTO;
import io.fireflysoftware.core.banking.ledger.models.entities.money.Money;
import io.fireflysoftware.core.banking.ledger.models.repositories.money.MoneyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MoneyServiceImplTest {

    @Mock
    private MoneyRepository repository;

    @Mock
    private MoneyMapper mapper;

    @InjectMocks
    private MoneyServiceImpl service;

    private MoneyDTO moneyDTO;
    private Money moneyEntity;
    private final UUID moneyId = UUID.randomUUID();
    private final String currency = "EUR";

    @BeforeEach
    void setUp() {
        // Initialize test data
        moneyDTO = new MoneyDTO();
        moneyDTO.setMoneyId(moneyId);
        moneyDTO.setAmount(new BigDecimal("1000.00"));
        moneyDTO.setCurrency(currency);

        moneyEntity = new Money();
        moneyEntity.setMoneyId(moneyId);
        moneyEntity.setAmount(new BigDecimal("1000.00"));
        moneyEntity.setCurrency(currency);
    }

    @Test
    void createMoney_Success() {
        // Arrange
        when(mapper.toEntity(any(MoneyDTO.class))).thenReturn(moneyEntity);
        when(repository.save(any(Money.class))).thenReturn(Mono.just(moneyEntity));
        when(mapper.toDTO(any(Money.class))).thenReturn(moneyDTO);

        // Act & Assert
        StepVerifier.create(service.createMoney(moneyDTO))
                .expectNext(moneyDTO)
                .verifyComplete();

        verify(mapper).toEntity(moneyDTO);
        verify(repository).save(moneyEntity);
        verify(mapper).toDTO(moneyEntity);
    }

    @Test
    void getMoney_Success() {
        // Arrange
        when(repository.findById(moneyId)).thenReturn(Mono.just(moneyEntity));
        when(mapper.toDTO(any(Money.class))).thenReturn(moneyDTO);

        // Act & Assert
        StepVerifier.create(service.getMoney(moneyId))
                .expectNext(moneyDTO)
                .verifyComplete();

        verify(repository).findById(moneyId);
        verify(mapper).toDTO(moneyEntity);
    }

    @Test
    void listMoney_Success() {
        // Arrange
        PaginationRequest paginationRequest = new PaginationRequest(0, 10, null, null);
        PaginationResponse<MoneyDTO> expectedResponse = new PaginationResponse<>(
                List.of(moneyDTO), 0, 10, 1
        );

        try (MockedStatic<PaginationUtils> paginationUtilsMocked = Mockito.mockStatic(PaginationUtils.class)) {
            paginationUtilsMocked.when(() -> PaginationUtils.paginateQuery(
                    eq(paginationRequest),
                    any(),
                    any(),
                    any()
            )).thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(service.listMoney(paginationRequest))
                    .expectNext(expectedResponse)
                    .verifyComplete();
        }
    }

    @Test
    void listMoneyByCurrency_Success() {
        // Arrange
        PaginationRequest paginationRequest = new PaginationRequest(0, 10, null, null);
        PaginationResponse<MoneyDTO> expectedResponse = new PaginationResponse<>(
                List.of(moneyDTO), 0, 10, 1
        );

        try (MockedStatic<PaginationUtils> paginationUtilsMocked = Mockito.mockStatic(PaginationUtils.class)) {
            paginationUtilsMocked.when(() -> PaginationUtils.paginateQuery(
                    eq(paginationRequest),
                    any(),
                    any(),
                    any()
            )).thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(service.listMoneyByCurrency(currency, paginationRequest))
                    .expectNext(expectedResponse)
                    .verifyComplete();
        }
    }
}
