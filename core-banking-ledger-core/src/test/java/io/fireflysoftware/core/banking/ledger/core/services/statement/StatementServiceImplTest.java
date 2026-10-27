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


package io.fireflysoftware.core.banking.ledger.core.services.statement;
import io.fireflysoftware.core.banking.ledger.core.services.statement.impl.StatementServiceImpl;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import io.fireflysoftware.core.banking.ledger.core.mappers.core.TransactionMapper;
import io.fireflysoftware.core.banking.ledger.core.mappers.statement.StatementMapper;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.statement.StatementMetadataDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.statement.StatementPeriodEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.statement.Statement;
import io.fireflysoftware.core.banking.ledger.models.repositories.statement.StatementRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementServiceImplTest {

    @Mock
    private StatementRepository repository;

    @Mock
    private StatementMapper mapper;



    @InjectMocks
    private StatementServiceImpl service;

    private StatementMetadataDTO metadataDTO;
    private Statement statement;
    private UUID accountId;
    private UUID accountSpaceId;
    private UUID statementId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        accountSpaceId = UUID.randomUUID();
        statementId = UUID.randomUUID();

        metadataDTO = new StatementMetadataDTO();
        metadataDTO.setStatementId(statementId);
        metadataDTO.setAccountId(accountId);
        metadataDTO.setPeriodType(StatementPeriodEnum.MONTHLY);
        metadataDTO.setStartDate(LocalDate.of(2023, 6, 1));
        metadataDTO.setEndDate(LocalDate.of(2023, 6, 30));
        metadataDTO.setGenerationDate(LocalDateTime.now());
        metadataDTO.setTransactionCount(10);
        metadataDTO.setIncludedPending(true);
        metadataDTO.setIncludedDetails(true);

        statement = new Statement();
        statement.setStatementId(statementId);
        statement.setAccountId(accountId);
        statement.setPeriodType(StatementPeriodEnum.MONTHLY);
        statement.setStartDate(LocalDate.of(2023, 6, 1));
        statement.setEndDate(LocalDate.of(2023, 6, 30));
        statement.setGenerationDate(LocalDateTime.now());
        statement.setTransactionCount(10);
        statement.setIncludedPending(true);
        statement.setIncludedDetails(true);
    }

    @Test
    void getStatement_Success() {
        when(repository.findById(statementId)).thenReturn(Mono.just(statement));
        when(mapper.toDTO(statement)).thenReturn(metadataDTO);

        StepVerifier.create(service.getStatement(statementId))
                .expectNext(metadataDTO)
                .verifyComplete();
    }

    @Test
    void listAccountStatements_Success() {
        PaginationRequest paginationRequest = new PaginationRequest(0, 10, null, null);
        PaginationResponse<StatementMetadataDTO> expectedResponse = new PaginationResponse<>(
                List.of(metadataDTO), 0, 10, 1
        );

        try (MockedStatic<PaginationUtils> paginationUtilsMocked = Mockito.mockStatic(PaginationUtils.class)) {
            paginationUtilsMocked.when(() -> PaginationUtils.paginateQuery(
                    eq(paginationRequest),
                    any(),
                    any(),
                    any()
            )).thenReturn(Mono.just(expectedResponse));

            StepVerifier.create(service.listAccountStatements(accountId, paginationRequest))
                    .expectNext(expectedResponse)
                    .verifyComplete();
        }
    }

    @Test
    void listAccountSpaceStatements_Success() {
        PaginationRequest paginationRequest = new PaginationRequest(0, 10, null, null);
        PaginationResponse<StatementMetadataDTO> expectedResponse = new PaginationResponse<>(
                List.of(metadataDTO), 0, 10, 1
        );

        try (MockedStatic<PaginationUtils> paginationUtilsMocked = Mockito.mockStatic(PaginationUtils.class)) {
            paginationUtilsMocked.when(() -> PaginationUtils.paginateQuery(
                    eq(paginationRequest),
                    any(),
                    any(),
                    any()
            )).thenReturn(Mono.just(expectedResponse));

            StepVerifier.create(service.listAccountSpaceStatements(accountSpaceId, paginationRequest))
                    .expectNext(expectedResponse)
                    .verifyComplete();
        }
    }

    @Test
    void listAccountStatementsByDateRange_Success() {
        LocalDate startDate = LocalDate.of(2023, 1, 1);
        LocalDate endDate = LocalDate.of(2023, 6, 30);
        PaginationRequest paginationRequest = new PaginationRequest(0, 10, null, null);
        PaginationResponse<StatementMetadataDTO> expectedResponse = new PaginationResponse<>(
                List.of(metadataDTO), 0, 10, 1
        );

        try (MockedStatic<PaginationUtils> paginationUtilsMocked = Mockito.mockStatic(PaginationUtils.class)) {
            paginationUtilsMocked.when(() -> PaginationUtils.paginateQuery(
                    eq(paginationRequest),
                    any(),
                    any(),
                    any()
            )).thenReturn(Mono.just(expectedResponse));

            StepVerifier.create(service.listAccountStatementsByDateRange(accountId, startDate, endDate, paginationRequest))
                    .expectNext(expectedResponse)
                    .verifyComplete();
        }
    }

    @Test
    void listAccountSpaceStatementsByDateRange_Success() {
        LocalDate startDate = LocalDate.of(2023, 1, 1);
        LocalDate endDate = LocalDate.of(2023, 6, 30);
        PaginationRequest paginationRequest = new PaginationRequest(0, 10, null, null);
        PaginationResponse<StatementMetadataDTO> expectedResponse = new PaginationResponse<>(
                List.of(metadataDTO), 0, 10, 1
        );

        try (MockedStatic<PaginationUtils> paginationUtilsMocked = Mockito.mockStatic(PaginationUtils.class)) {
            paginationUtilsMocked.when(() -> PaginationUtils.paginateQuery(
                    eq(paginationRequest),
                    any(),
                    any(),
                    any()
            )).thenReturn(Mono.just(expectedResponse));

            StepVerifier.create(service.listAccountSpaceStatementsByDateRange(accountSpaceId, startDate, endDate, paginationRequest))
                    .expectNext(expectedResponse)
                    .verifyComplete();
        }
    }
}
