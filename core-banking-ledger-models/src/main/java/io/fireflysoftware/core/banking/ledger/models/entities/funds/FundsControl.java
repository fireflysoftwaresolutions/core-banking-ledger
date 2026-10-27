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
@Table("ledger_funds_control")
public class FundsControl {
    @Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_EMPTY)
    private FundsControlId id;

    private UUID accountId;
    private String state;
}
