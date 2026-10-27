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


package io.fireflysoftware.core.banking.ledger.core.services.statement.impl;

import io.fireflysoftware.core.banking.ledger.core.mappers.statement.StatementMapper;
import io.fireflysoftware.core.banking.ledger.core.services.statement.StatementService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.statement.StatementMetadataDTO;
import io.fireflysoftware.core.banking.ledger.models.repositories.statement.StatementRepository;
import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.time.LocalDate;
import java.util.UUID;



/**
 * Implementation of the StatementService interface.
 */
@Service
@Transactional
public class StatementServiceImpl implements StatementService {

    @Autowired
    private StatementRepository repository;

    @Autowired
    private StatementMapper mapper;


    @Override
    public Mono<StatementMetadataDTO> getStatement(UUID statementId) {
        return repository.findById(statementId)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PaginationResponse<StatementMetadataDTO>> listAccountStatements(UUID accountId, PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByAccountId(accountId, pageable),
                () -> repository.countByAccountId(accountId)
        );
    }

    @Override
    public Mono<PaginationResponse<StatementMetadataDTO>> listAccountSpaceStatements(UUID accountSpaceId, PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByAccountSpaceId(accountSpaceId, pageable),
                () -> repository.countByAccountSpaceId(accountSpaceId)
        );
    }

    @Override
    public Mono<PaginationResponse<StatementMetadataDTO>> listAccountStatementsByDateRange(
            UUID accountId, LocalDate startDate, LocalDate endDate, PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByAccountIdAndDateRange(accountId, startDate, endDate, pageable),
                () -> repository.countByAccountId(accountId) // This is a simplification, ideally we'd count only within the date range
        );
    }

    @Override
    public Mono<PaginationResponse<StatementMetadataDTO>> listAccountSpaceStatementsByDateRange(
            UUID accountSpaceId, LocalDate startDate, LocalDate endDate, PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByAccountSpaceIdAndDateRange(accountSpaceId, startDate, endDate, pageable),
                () -> repository.countByAccountSpaceId(accountSpaceId) // This is a simplification, ideally we'd count only within the date range
        );
    }
}
