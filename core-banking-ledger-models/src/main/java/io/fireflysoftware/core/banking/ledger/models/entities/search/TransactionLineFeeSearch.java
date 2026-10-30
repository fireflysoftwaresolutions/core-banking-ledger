package io.fireflysoftware.core.banking.ledger.models.entities.search;

import io.fireflysoftware.core.banking.ledger.models.entities.fee.TransactionLineFee;

import lombok.Getter;
import lombok.Setter;

import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Getter
@Setter
@Table("ledger_search_transaction_line_fee")
public class TransactionLineFeeSearch extends TransactionLineFee {
    @ReadOnlyProperty private BigDecimal ledgerTransactionTotalAmount;
}
