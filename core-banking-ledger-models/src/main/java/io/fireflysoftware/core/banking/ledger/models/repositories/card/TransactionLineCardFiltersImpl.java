package io.fireflysoftware.core.banking.ledger.models.repositories.card;

import io.fireflysoftware.core.banking.ledger.models.entities.card.TransactionLineCard;
import io.fireflysoftware.core.banking.ledger.models.entities.search.TransactionLineCardSearch;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionLineCardFiltersImpl implements TransactionLineCardFilters {
    private final R2dbcEntityOperations operations;

    public TransactionLineCardFiltersImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Flux<TransactionLineCard> findByCardMerchantCode(
            String merchantCode, Pageable pageable) {
        if (merchantCode == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("card_merchant_category_code").is(merchantCode));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("card_transaction_timestamp")));
        return operations.select(query, TransactionLineCard.class);
    }

    @Override
    public Mono<Long> countByCardMerchantCode(String merchantCode) {
        if (merchantCode == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("card_merchant_category_code").is(merchantCode));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineCard.class);
    }

    @Override
    public Flux<TransactionLineCard> findByCardTerminalId(String terminalId, Pageable pageable) {
        if (terminalId == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("card_terminal_id").is(terminalId));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("card_transaction_timestamp")));
        return operations.select(query, TransactionLineCard.class);
    }

    @Override
    public Flux<TransactionLineCard> findByCardFraudFlag(Boolean fraudFlag, Pageable pageable) {
        if (fraudFlag == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("card_fraud_flag").is(fraudFlag));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("card_transaction_timestamp")));
        return operations.select(query, TransactionLineCard.class);
    }

    @Override
    public Flux<TransactionLineCard> findByMerchantNameContaining(
            String merchantName, Pageable pageable) {
        if (merchantName == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(
                        Criteria.where("card_merchant_name")
                                .like("%" + merchantName + "%")
                                .ignoreCase(true));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("card_transaction_timestamp")));
        return operations.select(query, TransactionLineCard.class);
    }

    @Override
    public Mono<Long> countByMerchantNameContaining(String merchantName) {
        if (merchantName == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria =
                criteria.and(
                        Criteria.where("card_merchant_name")
                                .like("%" + merchantName + "%")
                                .ignoreCase(true));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineCard.class);
    }

    @Override
    public Flux<TransactionLineCard> findFraudulentTransactionsByCountry(
            String country, Pageable pageable) {
        if (country == null) return Flux.empty();
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("card_holder_country").is(country));
        criteria = criteria.and(Criteria.where("card_fraud_flag").is(true));
        Query query = Query.query(criteria);
        query = query.with(pageable);
        query = query.sort(Sort.by(Sort.Order.desc("card_transaction_timestamp")));
        return operations.select(query, TransactionLineCard.class);
    }

    @Override
    public Mono<Long> countFraudulentTransactionsByCountry(String country) {
        if (country == null) return Mono.just(0L);
        Criteria criteria = Criteria.empty();
        criteria = criteria.and(Criteria.where("card_holder_country").is(country));
        criteria = criteria.and(Criteria.where("card_fraud_flag").is(true));
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineCard.class);
    }

    @Override
    public Flux<TransactionLineCard> findByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String merchantCode,
            Boolean cardPresent,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("card_transaction_timestamp")
                                    .greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("card_transaction_timestamp").lessThanOrEquals(endDate));
        if (merchantCode != null)
            criteria = criteria.and(Criteria.where("card_merchant_category_code").is(merchantCode));
        if (cardPresent != null)
            criteria = criteria.and(Criteria.where("card_present_flag").is(cardPresent));
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
        Query query = Query.query(criteria);
        query = query.with(pageable);
        return operations
                .select(query, TransactionLineCardSearch.class)
                .map(row -> (TransactionLineCard) row);
    }

    @Override
    public Mono<Long> countByCustomCriteria(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String merchantCode,
            Boolean cardPresent,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        Criteria criteria = Criteria.empty();
        if (startDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("card_transaction_timestamp")
                                    .greaterThanOrEquals(startDate));
        if (endDate != null)
            criteria =
                    criteria.and(
                            Criteria.where("card_transaction_timestamp").lessThanOrEquals(endDate));
        if (merchantCode != null)
            criteria = criteria.and(Criteria.where("card_merchant_category_code").is(merchantCode));
        if (cardPresent != null)
            criteria = criteria.and(Criteria.where("card_present_flag").is(cardPresent));
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
        Query query = Query.query(criteria);
        return operations.count(query, TransactionLineCardSearch.class);
    }
}
