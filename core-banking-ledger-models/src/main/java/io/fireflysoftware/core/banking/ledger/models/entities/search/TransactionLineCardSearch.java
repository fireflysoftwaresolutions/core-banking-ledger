package io.fireflysoftware.core.banking.ledger.models.entities.search;

import io.fireflysoftware.core.banking.ledger.models.entities.card.TransactionLineCard;

import lombok.Getter;
import lombok.Setter;

import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Getter
@Setter
@Table("ledger_search_transaction_line_card")
public class TransactionLineCardSearch extends TransactionLineCard {
    @ReadOnlyProperty private BigDecimal ledgerTransactionTotalAmount;
}
