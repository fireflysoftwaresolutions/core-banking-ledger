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
@Table("ledger_funds_event")
public class FundsEvent {
    @Id private UUID eventId;
    private String tenantId;
    private UUID resourceId;
    private String actor;
    private String eventType;
    private Json payload;
}
