package io.fireflysoftware.core.banking.ledger.models.entities.funds;

import io.r2dbc.postgresql.codec.Json;

import lombok.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("event_outbox")
public class FundsOutbox {
    @Id private UUID eventId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private Json payload;
    private String fundsTenantId;
}
