package io.fireflysoftware.core.banking.ledger.models.entities.search;

import io.fireflysoftware.core.banking.ledger.models.entities.sepa.TransactionLineSepaTransfer;

import lombok.Getter;
import lombok.Setter;

import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Getter
@Setter
@Table("ledger_search_transaction_line_sepa_transfer")
public class TransactionLineSepaTransferSearch extends TransactionLineSepaTransfer {
    @ReadOnlyProperty private BigDecimal ledgerTransactionTotalAmount;
}
