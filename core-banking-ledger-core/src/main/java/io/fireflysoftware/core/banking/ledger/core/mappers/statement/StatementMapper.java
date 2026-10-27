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


package io.fireflysoftware.core.banking.ledger.core.mappers.statement;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.statement.StatementMetadataDTO;
import io.fireflysoftware.core.banking.ledger.models.entities.statement.Statement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * Mapper for converting between Statement entity and StatementMetadataDTO.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface StatementMapper {
    /**
     * Convert a Statement entity to a StatementMetadataDTO.
     *
     * @param entity The Statement entity.
     * @return The StatementMetadataDTO.
     */
    @Mapping(target = "dateCreated", source = "dateCreated")
    @Mapping(target = "dateUpdated", source = "dateUpdated")
    StatementMetadataDTO toDTO(Statement entity);

    /**
     * Convert a StatementMetadataDTO to a Statement entity.
     *
     * @param dto The StatementMetadataDTO.
     * @return The Statement entity.
     */
    @Mapping(target = "dateCreated", ignore = true)
    @Mapping(target = "dateUpdated", ignore = true)
    Statement toEntity(StatementMetadataDTO dto);
}
