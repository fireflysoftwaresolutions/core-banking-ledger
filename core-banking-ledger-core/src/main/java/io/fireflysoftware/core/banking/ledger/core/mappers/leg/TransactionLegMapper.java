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


package io.fireflysoftware.core.banking.ledger.core.mappers.leg;

import io.fireflysoftware.core.banking.ledger.core.mappers.BaseMapper;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.leg.TransactionLegDTO;
import io.fireflysoftware.core.banking.ledger.models.entities.leg.TransactionLeg;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * Mapper for converting between TransactionLeg entity and DTO.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TransactionLegMapper extends BaseMapper<TransactionLeg, TransactionLegDTO> {
    @Override
    @Mapping(target = "dateCreated", ignore = true)
    @Mapping(target = "dateUpdated", ignore = true)
    TransactionLeg toEntity(TransactionLegDTO dto);

    @Override
    TransactionLegDTO toDTO(TransactionLeg entity);
}
