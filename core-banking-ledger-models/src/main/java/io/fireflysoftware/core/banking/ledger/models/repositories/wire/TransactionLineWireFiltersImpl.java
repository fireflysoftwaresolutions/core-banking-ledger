package io.fireflysoftware.core.banking.ledger.models.repositories.wire;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.wire.WireTransferPriorityEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.search.TransactionLineWireTransferSearch;
import io.fireflysoftware.core.banking.ledger.models.entities.wire.TransactionLineWireTransfer;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionLineWireFiltersImpl implements TransactionLineWireFilters {
    private final R2dbcEntityOperations operations;

    public TransactionLineWireFiltersImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Flux<TransactionLineWireTransfer> findByWireCancelledFlag(
            Boolean cancelled, Pageable pageable) {
        if (cancelled == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("wire_cancelled_flag").is(cancelled));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineWireTransfer.class);
    }

    @Override
    public Mono<Long> countByWireCancelledFlag(Boolean cancelled) {
        if (cancelled == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("wire_cancelled_flag").is(cancelled));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineWireTransfer.class);
    }

    @Override
    public Flux<TransactionLineWireTransfer> findByWireTransferPriority(
            WireTransferPriorityEnum priority, Pageable pageable) {
        if (priority == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("wire_transfer_priority").is(priority));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineWireTransfer.class);
    }

    @Override
    public Mono<Long> countByWireTransferPriority(WireTransferPriorityEnum priority) {
        if (priority == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("wire_transfer_priority").is(priority));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineWireTransfer.class);
    }

    @Override
    public Flux<TransactionLineWireTransfer> findOutgoingTransfersBySwift(
            String swiftCode, Pageable pageable) {
        if (swiftCode == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("wire_origin_swift_bic").is(swiftCode));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineWireTransfer.class);
    }

    @Override
    public Mono<Long> countOutgoingTransfersBySwift(String swiftCode) {
        if (swiftCode == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("wire_origin_swift_bic").is(swiftCode));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineWireTransfer.class);
    }

    @Override
    public Flux<TransactionLineWireTransfer> findIncomingTransfersBySwift(
            String swiftCode, Pageable pageable) {
        if (swiftCode == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("wire_destination_swift_bic").is(swiftCode));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineWireTransfer.class);
    }

    @Override
    public Mono<Long> countIncomingTransfersBySwift(String swiftCode) {
        if (swiftCode == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("wire_destination_swift_bic").is(swiftCode));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineWireTransfer.class);
    }

    @Override
    public Flux<TransactionLineWireTransfer> findByBeneficiaryNameContaining(
            String beneficiaryName, Pageable pageable) {
        if (beneficiaryName == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(
                        Criteria.where("wire_beneficiary_name")
                                .like("%" + beneficiaryName + "%")
                                .ignoreCase(true));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineWireTransfer.class);
    }

    @Override
    public Mono<Long> countByBeneficiaryNameContaining(String beneficiaryName) {
        if (beneficiaryName == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(
                        Criteria.where("wire_beneficiary_name")
                                .like("%" + beneficiaryName + "%")
                                .ignoreCase(true));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineWireTransfer.class);
    }

    @Override
    public Flux<TransactionLineWireTransfer> findPendingTransfers(Pageable pageable) {
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("wire_cancelled_flag").is(false));
        criteria = criteria.and(Criteria.where("wire_reception_status").is("PENDING"));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query =
                query.sort(
                        Sort.by(
                                Sort.Order.asc("wire_transfer_priority"),
                                Sort.Order.asc("wire_processing_date")));
        return operations.select(query, TransactionLineWireTransfer.class);
    }

    @Override
    public Mono<Long> countPendingTransfers() {
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("wire_cancelled_flag").is(false));
        criteria = criteria.and(Criteria.where("wire_reception_status").is("PENDING"));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineWireTransfer.class);
    }

    @Override
    public Flux<TransactionLineWireTransfer> findByExchangeRateRange(
            Double minRate, Double maxRate, Pageable pageable) {
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("wire_exchange_rate").isNotNull());
        if (minRate != null)
            criteria =
                    criteria.and(Criteria.where("wire_exchange_rate").greaterThanOrEquals(minRate));
        if (maxRate != null)
            criteria = criteria.and(Criteria.where("wire_exchange_rate").lessThanOrEquals(maxRate));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations.select(query, TransactionLineWireTransfer.class);
    }

    @Override
    public Mono<Long> countByExchangeRateRange(Double minRate, Double maxRate) {
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("wire_exchange_rate").isNotNull());
        if (minRate != null)
            criteria =
                    criteria.and(Criteria.where("wire_exchange_rate").greaterThanOrEquals(minRate));
        if (maxRate != null)
            criteria = criteria.and(Criteria.where("wire_exchange_rate").lessThanOrEquals(maxRate));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineWireTransfer.class);
    }

    @Override
    public Flux<TransactionLineWireTransfer> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            WireTransferPriorityEnum priority,
            Boolean cancelled,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String beneficiaryName,
            String originSwiftCode,
            String destinationSwiftCode,
            BigDecimal minExchangeRate,
            BigDecimal maxExchangeRate,
            BigDecimal minFeeAmount,
            BigDecimal maxFeeAmount,
            Pageable pageable) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("wire_processing_date").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria =
                    criteria.and(Criteria.where("wire_processing_date").lessThanOrEquals(endDate));
        if (priority != null)
            criteria = criteria.and(Criteria.where("wire_transfer_priority").is(priority));
        if (cancelled != null)
            criteria = criteria.and(Criteria.where("wire_cancelled_flag").is(cancelled));
        if (minAmount != null)
            criteria =
                    criteria.and(
                            Criteria.where("ledger_transaction_total_amount")
                                    .greaterThanOrEquals(minAmount));
        if (maxAmount != null)
            criteria =
                    criteria.and(
                            Criteria.where("ledger_transaction_total_amount")
                                    .lessThanOrEquals(maxAmount));
        if (beneficiaryName != null)
            criteria =
                    criteria.and(
                            Criteria.where("wire_beneficiary_name")
                                    .like("%" + beneficiaryName + "%")
                                    .ignoreCase(true));
        if (originSwiftCode != null)
            criteria = criteria.and(Criteria.where("wire_origin_swift_bic").is(originSwiftCode));
        if (destinationSwiftCode != null)
            criteria =
                    criteria.and(
                            Criteria.where("wire_destination_swift_bic").is(destinationSwiftCode));
        if (minExchangeRate != null)
            criteria =
                    criteria.and(
                            Criteria.where("wire_exchange_rate")
                                    .greaterThanOrEquals(minExchangeRate));
        if (maxExchangeRate != null)
            criteria =
                    criteria.and(
                            Criteria.where("wire_exchange_rate").lessThanOrEquals(maxExchangeRate));
        if (minFeeAmount != null)
            criteria =
                    criteria.and(
                            Criteria.where("wire_fee_amount").greaterThanOrEquals(minFeeAmount));
        if (maxFeeAmount != null)
            criteria =
                    criteria.and(Criteria.where("wire_fee_amount").lessThanOrEquals(maxFeeAmount));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations
                .select(query, TransactionLineWireTransferSearch.class)
                .map(row -> (TransactionLineWireTransfer) row);
    }

    @Override
    public Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            WireTransferPriorityEnum priority,
            Boolean cancelled,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String beneficiaryName,
            String originSwiftCode,
            String destinationSwiftCode,
            BigDecimal minExchangeRate,
            BigDecimal maxExchangeRate,
            BigDecimal minFeeAmount,
            BigDecimal maxFeeAmount) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("wire_processing_date").greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria =
                    criteria.and(Criteria.where("wire_processing_date").lessThanOrEquals(endDate));
        if (priority != null)
            criteria = criteria.and(Criteria.where("wire_transfer_priority").is(priority));
        if (cancelled != null)
            criteria = criteria.and(Criteria.where("wire_cancelled_flag").is(cancelled));
        if (minAmount != null)
            criteria =
                    criteria.and(
                            Criteria.where("ledger_transaction_total_amount")
                                    .greaterThanOrEquals(minAmount));
        if (maxAmount != null)
            criteria =
                    criteria.and(
                            Criteria.where("ledger_transaction_total_amount")
                                    .lessThanOrEquals(maxAmount));
        if (beneficiaryName != null)
            criteria =
                    criteria.and(
                            Criteria.where("wire_beneficiary_name")
                                    .like("%" + beneficiaryName + "%")
                                    .ignoreCase(true));
        if (originSwiftCode != null)
            criteria = criteria.and(Criteria.where("wire_origin_swift_bic").is(originSwiftCode));
        if (destinationSwiftCode != null)
            criteria =
                    criteria.and(
                            Criteria.where("wire_destination_swift_bic").is(destinationSwiftCode));
        if (minExchangeRate != null)
            criteria =
                    criteria.and(
                            Criteria.where("wire_exchange_rate")
                                    .greaterThanOrEquals(minExchangeRate));
        if (maxExchangeRate != null)
            criteria =
                    criteria.and(
                            Criteria.where("wire_exchange_rate").lessThanOrEquals(maxExchangeRate));
        if (minFeeAmount != null)
            criteria =
                    criteria.and(
                            Criteria.where("wire_fee_amount").greaterThanOrEquals(minFeeAmount));
        if (maxFeeAmount != null)
            criteria =
                    criteria.and(Criteria.where("wire_fee_amount").lessThanOrEquals(maxFeeAmount));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineWireTransferSearch.class);
    }
}
