package io.fireflysoftware.core.banking.ledger.models.entities.funds;

import lombok.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ledger_funds_action")
public class FundsAction {
    @Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_EMPTY)
    private FundsActionId id;

    private UUID reservationId;
    private String action;
}
