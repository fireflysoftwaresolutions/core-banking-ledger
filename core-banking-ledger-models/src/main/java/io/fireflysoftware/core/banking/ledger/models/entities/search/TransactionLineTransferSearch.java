package io.fireflysoftware.core.banking.ledger.models.entities.search;

import io.fireflysoftware.core.banking.ledger.models.entities.transfer.TransactionLineTransfer;

import lombok.Getter;
import lombok.Setter;

import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Getter
@Setter
@Table("ledger_search_transaction_line_transfer")
public class TransactionLineTransferSearch extends TransactionLineTransfer {
    @ReadOnlyProperty private BigDecimal ledgerTransactionTotalAmount;
}
