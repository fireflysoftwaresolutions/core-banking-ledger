/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.core.impl;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerAuditRecordService;
import io.fireflysoftware.core.banking.ledger.core.services.funds.ownership.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsReceiptContracts.*;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec;
import io.fireflysoftware.core.banking.ledger.models.entities.core.*;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.*;
import io.fireflysoftware.core.banking.ledger.models.entities.leg.TransactionLeg;
import io.fireflysoftware.core.banking.ledger.models.repositories.core.*;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsRepositories;
import io.fireflysoftware.core.banking.ledger.models.repositories.leg.TransactionLegRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.fireflyframework.core.transaction.*;
import org.springframework.data.domain.PageRequest;
import reactor.core.publisher.Mono;

/** Reads actual provisional durable records on the exact original owning transaction graph. */
public final class LedgerAuditRecordServiceImpl implements LedgerAuditRecordService {
    private final BoundedR2dbcResources resources;
    private final FundsRepositories funds;
    private final TransactionRepository transactions;
    private final TransactionLegRepository legs;
    private final TransactionStatusHistoryRepository history;
    public LedgerAuditRecordServiceImpl(BoundedR2dbcResources resources, FundsRepositories funds,
            TransactionRepository transactions, TransactionLegRepository legs, TransactionStatusHistoryRepository history) {
        this.resources=Objects.requireNonNull(resources);this.funds=Objects.requireNonNull(funds);
        this.transactions=Objects.requireNonNull(transactions);this.legs=Objects.requireNonNull(legs);this.history=Objects.requireNonNull(history);
    }
    public Mono<LedgerAuditEvidence> observe(RootTransactionScope scope, LedgerMutation mutation, String tenant) {
        return Mono.defer(()->resources.factory().requireBoundScope(resources,scope).then(Mono.defer(()->{
            require(tenant!=null&&tenant.matches("[A-Za-z0-9_-]{1,64}"));
            if(mutation instanceof LedgerMutation.Created m) return posting(postingId(m.result()),TransactionStatusEnum.valueOf(m.request().getTransaction().getTransactionStatus().name())).map(p->{
                require(m.request().getIdempotencyKey().equals(p.claim().getIdempotencyKey())&&LedgerPostingServiceImpl.fingerprint(m.request(),null).equals(p.claim().getInputFingerprint())&&p.value().getRelatedTransactionId()==null);
                return postingEvidence("POSTING_CREATE_CAPTURED",tenant,p.value().getTransactionId(),m.request().getIdempotencyKey(),p);
            });
            if(mutation instanceof LedgerMutation.Confirmed m) { require(m.postingId().equals(postingId(m.result())));return posting(m.postingId(),TransactionStatusEnum.POSTED).map(p->postingEvidence("POSTING_CONFIRM_CAPTURED",tenant,m.postingId(),m.postingId().toString(),p)); }
            if(mutation instanceof LedgerMutation.Released m) { require(m.postingId().equals(postingId(m.result())));return posting(m.postingId(),TransactionStatusEnum.REVERSED).map(p->postingEvidence("POSTING_RELEASE_CAPTURED",tenant,m.postingId(),m.postingId().toString(),p)); }
            if(mutation instanceof LedgerMutation.Reversed m) return posting(postingId(m.result()),TransactionStatusEnum.POSTED).map(p->{require(m.postingId().equals(p.value().getRelatedTransactionId())&&"REVERSAL".equals(p.value().getRelationType())&&m.key().equals(p.claim().getIdempotencyKey()));return postingEvidence("POSTING_REVERSE_CAPTURED",tenant,m.postingId(),m.key(),p);});
            if(mutation instanceof LedgerMutation.Funded m) { require(tenant.equals(m.tenant()));return funding(tenant,m); }
            if(mutation instanceof LedgerMutation.Reserved m) { require(tenant.equals(m.tenant()));return accepted(tenant,m.result()).flatMap(original->{var a=original.accepted();var op=original.operation();return posting(m.result().postingId(),TransactionStatusEnum.PENDING).map(p->{
                require(m.request().operationId().equals(a.intent().operationId())&&a.reservationId().equals(op.getReservationId())&&FundsReceiptCodec.canonicalIntent(tenant,m.request()).equals(FundsReceiptCodec.canonicalIntent(tenant,a.intent())));
                requireAccounts(p,a.intent().sourceAccountId(),a.intent().destinationAccountId(),a.intent().amount(),a.intent().currency());
                var d=base("FUNDS_RESERVE_CAPTURED",tenant,a.reservationId(),a.intent().operationId(),m.request().operationId().toString()).field(FundsReceiptCodec.canonicalAccepted(a).canonical()).field(op.getFingerprint());operationFields(d,op);postingFields(d,p);
                return evidence("FUNDS_RESERVE_CAPTURED",tenant,"funds-reservation",a.reservationId(),a.intent().operationId(),m.request().operationId().toString(),d,a.intentRecordedAtEpochMillis(),null,null);
            });}); }
            if(mutation instanceof LedgerMutation.Acted m) { require(tenant.equals(m.tenant())&&m.reservationId().equals(m.result().reservationId()));return action(tenant,m); }
            return Mono.error(failure());
        })).timeout(scope.remaining()));
    }
    private Mono<LedgerAuditEvidence> funding(String tenant,LedgerMutation.Funded m) {
        require(m.request().operationId().equals(m.result().operationId()));
        return operation(tenant,m.request().operationId(),"FUNDING").flatMap(op->{
            require(m.result().postingId().equals(op.getPostingId())&&m.request().bankControlAccountId().equals(op.getSourceAccountId())&&m.request().customerAccountId().equals(op.getDestinationAccountId())&&fundingFingerprint(m.request()).equals(op.getFingerprint()));
            return posting(op.getPostingId(),TransactionStatusEnum.POSTED).flatMap(p->funds.events().findByTenantIdAndResourceIdAndEventTypeOrderByEventId(tenant,m.request().operationId(),"BANK_CONTROL_FUNDING",PageRequest.of(0,2)).collectList().map(rows->{
                require(rows.size()==1);var event=rows.getFirst();require(event.getEventId()!=null&&tenant.equals(event.getTenantId())&&m.request().operationId().equals(event.getResourceId())&&event.getActor()!=null&&!event.getActor().isBlank());
                requireAccounts(p,m.request().bankControlAccountId(),m.request().customerAccountId(),m.request().amount(),m.request().currency());
                require("BANK_CONTROL_FUNDING".equals(event.getEventType())&&event.getPayload()!=null);
                var d=base("FUNDS_FUND_CAPTURED",tenant,m.request().operationId(),m.request().operationId(),m.request().operationId().toString()).field(op.getFingerprint()).field(event.getEventId()).field(event.getActor()).field(event.getEventType()).field(event.getPayload().asString());postingFields(d,p);
                return evidence("FUNDS_FUND_CAPTURED",tenant,"funds-operation",m.request().operationId(),m.request().operationId(),m.request().operationId().toString(),d,null,null,event.getActor());
            }));
        });
    }
    private Mono<LedgerAuditEvidence> action(String tenant,LedgerMutation.Acted m) {
        require(m.request()!=null&&m.request().actionId()!=null&&m.request().action()!=null);
        return accepted(tenant,m.result()).flatMap(original->{var a=original.accepted();return funds.actions().findById(new FundsActionId(tenant,m.request().actionId())).switchIfEmpty(Mono.error(failure())).flatMap(row->{
            require(new FundsActionId(tenant,m.request().actionId()).equals(row.getId())&&m.reservationId().equals(row.getReservationId())&&m.request().action().name().equals(row.getAction()));
            String kind="FUNDS_"+m.request().action().name()+"_CAPTURED",key=m.request().actionId().toString();
            var d=base(kind,tenant,m.reservationId(),a.intent().operationId(),key).field(FundsReceiptCodec.canonicalAccepted(a).canonical()).field(row.getAction());operationFields(d,original.operation());
            if(m.request().action()==Action.RELEASE||m.request().action()==Action.EXPIRE)
                return posting(m.result().postingId(),TransactionStatusEnum.REVERSED).map(p->{requireAccounts(p,a.intent().sourceAccountId(),a.intent().destinationAccountId(),a.intent().amount(),a.intent().currency());postingFields(d,p);return evidence(kind,tenant,"funds-reservation",m.reservationId(),a.intent().operationId(),key,d,null,null,null);});
            return funds.receipts().findTop3ByTenantIdAndReservationIdOrderByEffectKindAsc(tenant,m.reservationId()).collectList().flatMap(rows->{
                var values=new EnumMap<EffectKind,MonetaryReceipt>(EffectKind.class);
                for(var retained:rows){var v=FundsReceiptCodec.decodeReceipt(retained.getReceiptCanonical());require(tenant.equals(retained.getTenantId())&&m.reservationId().equals(retained.getReservationId())&&retained.getReceiptId().equals(v.receiptId())&&retained.getEffectKind().equals(v.effectKind().name())&&FundsReceiptCodec.canonicalReceipt(v).digest().equals(retained.getReceiptDigest())&&FundsReceiptCodec.canonicalAccepted(v.acceptedReserve()).equals(FundsReceiptCodec.canonicalAccepted(a))&&values.put(v.effectKind(),v)==null);}
                var selected=values.get(m.request().action()==Action.COMMIT?EffectKind.COMMIT:EffectKind.REVERSE);require(selected!=null&&m.request().actionId().equals(selected.actionId()));
                if(m.request().action()==Action.REVERSE){require(values.get(EffectKind.COMMIT)!=null);FundsReceiptCodec.validateReversal(selected,values.get(EffectKind.COMMIT));}
                d.field(FundsReceiptCodec.canonicalReceipt(selected).canonical());
                return posting(selected.postingId(),TransactionStatusEnum.POSTED).map(p->{
                    requireAccounts(p,selected.effect().fromAccountId(),selected.effect().toAccountId(),a.intent().amount(),a.intent().currency());
                    if(m.request().action()==Action.REVERSE)require(selected.originalPostingId().equals(p.value().getRelatedTransactionId())&&"REVERSAL".equals(p.value().getRelationType()));
                    else require(selected.postingId().equals(m.result().postingId()));
                    postingFields(d,p);return evidence(kind,tenant,"funds-reservation",m.reservationId(),a.intent().operationId(),key,d,selected.effectRecordedAtEpochMillis(),selected.actor().issuer(),selected.actor().subject());
                });
            });
        });});
    }
    private record OriginalReserve(AcceptedReserve accepted,FundsOperation operation) {}
    private Mono<OriginalReserve> accepted(String tenant,Reservation result) {
        return funds.reservations().findByTenantIdAndReservationId(tenant,result.reservationId()).switchIfEmpty(Mono.error(failure())).flatMap(row->{
            require(tenant.equals(row.getTenantId())&&result.reservationId().equals(row.getReservationId())&&result.operationId().equals(row.getOperationId())&&result.sourceAccountId().equals(row.getSourceAccountId())&&result.destinationAccountId().equals(row.getDestinationAccountId())&&result.amount().compareTo(row.getAmount())==0&&result.currency().equals(row.getCurrency())&&result.postingId().equals(row.getPostingId()));
            return funds.originals().findByTenantIdAndReservationId(tenant,row.getReservationId()).switchIfEmpty(Mono.error(failure())).map(original->{
                var a=FundsReceiptCodec.decodeAccepted(original.getAcceptedCanonical());require(tenant.equals(original.getTenantId())&&row.getReservationId().equals(original.getReservationId())&&row.getOperationId().equals(original.getOperationId())&&FundsReceiptCodec.canonicalAccepted(a).digest().equals(original.getAcceptedDigest())&&tenant.equals(a.tenant())&&row.getReservationId().equals(a.reservationId())&&row.getOperationId().equals(a.intent().operationId())&&row.getSourceAccountId().equals(a.intent().sourceAccountId())&&row.getDestinationAccountId().equals(a.intent().destinationAccountId())&&row.getAmount().compareTo(a.intent().amount())==0&&row.getCurrency().equals(a.intent().currency()));return a;
            }).flatMap(a->operation(tenant,a.intent().operationId(),"RESERVE").map(op->{
                require(a.reservationId().equals(op.getReservationId())&&op.getPostingId()==null
                        &&a.intent().sourceAccountId().equals(op.getSourceAccountId())&&a.intent().destinationAccountId().equals(op.getDestinationAccountId())
                        &&FundsReceiptCodec.reserveOperationFingerprint(a.intent()).equals(op.getFingerprint())
                        &&Boolean.FALSE.equals(op.getClosedWithoutEffect())&&op.getClosureReceiptId()==null&&op.getClosureObservedAt()==null
                        &&op.getNotAfterEpochMillis()==null&&op.getCanonicalIntent()==null&&op.getClosedPostingKey()==null);
                return new OriginalReserve(a,op);
            }));
        });
    }
    private Mono<FundsOperation> operation(String tenant,UUID id,String kind) { return funds.operations().findById(new FundsOperationId(tenant,id)).switchIfEmpty(Mono.error(failure())).map(row->{require(new FundsOperationId(tenant,id).equals(row.getId())&&kind.equals(row.getKind())&&row.getFingerprint()!=null&&row.getFingerprint().matches("[0-9a-f]{64}")&&!Boolean.TRUE.equals(row.getClosedWithoutEffect()));return row;}); }
    private record SelectedHistory(TransactionStatusHistory value,UUID linkedReversal) {}
    private record Posting(Transaction value,LedgerPostingClaim claim,List<TransactionLeg> legs,SelectedHistory history) {}
    private Mono<SelectedHistory> status(UUID id,TransactionStatusEnum status) {
        return history.findByTransactionIdAndStatusCodeOrderByTransactionStatusHistoryId(id,status,PageRequest.of(0,2)).collectList().flatMap(rows->{
            if(rows.size()==1){var h=rows.getFirst();require(h.getTransactionStatusHistoryId()!=null&&id.equals(h.getTransactionId())&&status==h.getStatusCode());return Mono.just(new SelectedHistory(h,null));}
            if(rows.isEmpty()&&status==TransactionStatusEnum.REVERSED)return transactions.findManagedPostedReversal(id).switchIfEmpty(Mono.error(failure())).flatMap(reverse->{require(id.equals(reverse.getRelatedTransactionId())&&"REVERSAL".equals(reverse.getRelationType())&&reverse.getTransactionStatus()==TransactionStatusEnum.POSTED);return status(id,TransactionStatusEnum.POSTED).map(h->new SelectedHistory(h.value(),reverse.getTransactionId()));});
            return Mono.error(failure());
        });
    }
    private Mono<Posting> posting(UUID id,TransactionStatusEnum selectedStatus) {
        return transactions.findById(id).switchIfEmpty(Mono.error(failure())).flatMap(t->{
            require(id.equals(t.getTransactionId())&&t.isManagedPosting()&&t.getExternalReference()!=null&&t.getAccountId()!=null&&t.getTotalAmount()!=null&&t.getTotalAmount().signum()>0&&t.getCurrency()!=null);
            return Mono.zip(funds.postings().findById(t.getExternalReference()).switchIfEmpty(Mono.error(failure())),legs.findByTransactionIdOrderByTransactionLegId(id,PageRequest.of(0,1001)).collectList(),status(id,selectedStatus)).map(tuple->{
                var claim=tuple.getT1();var details=tuple.getT2();require(t.getExternalReference().equals(claim.getIdempotencyKey())&&id.equals(claim.getTransactionId())&&claim.getInputFingerprint()!=null&&claim.getInputFingerprint().matches("[0-9a-f]{64}")&&details.size()>=2&&details.size()<=1000);
                var ids=new HashSet<UUID>();BigDecimal debit=BigDecimal.ZERO,credit=BigDecimal.ZERO;
                for(var leg:details){require(leg.getTransactionLegId()!=null&&ids.add(leg.getTransactionLegId())&&id.equals(leg.getTransactionId())&&leg.getAccountId()!=null&&t.getCurrency().equals(leg.getCurrency())&&leg.getAmount()!=null&&leg.getAmount().signum()>0);if("DEBIT".equals(leg.getLegType()))debit=debit.add(leg.getAmount());else {require("CREDIT".equals(leg.getLegType()));credit=credit.add(leg.getAmount());}}
                require(debit.compareTo(credit)==0&&debit.compareTo(t.getTotalAmount())==0);return new Posting(t,claim,List.copyOf(details),tuple.getT3());
            });
        });
    }
    private static void operationFields(LedgerAuditDigest d,FundsOperation op) {
        d.field(op.getId().tenantId()).field(op.getId().operationId()).field(op.getKind()).field(op.getFingerprint())
                .field(op.getReservationId()).field(op.getPostingId()).field(op.getSourceAccountId()).field(op.getDestinationAccountId())
                .field(op.getClosedWithoutEffect());
    }
    private static void requireAccounts(Posting p,UUID debit,UUID credit,BigDecimal amount,String currency) {
        require(p.value().getAccountId().equals(debit)&&p.value().getTotalAmount().compareTo(amount)==0&&currency.equals(p.value().getCurrency())&&p.legs().size()==2);
        for(var leg:p.legs())require(("DEBIT".equals(leg.getLegType())?debit:credit).equals(leg.getAccountId())&&leg.getAmount().compareTo(amount)==0);
    }
    private static UUID postingId(LedgerPostingDTO result) { require(result!=null&&result.getTransaction()!=null&&result.getTransaction().getTransactionId()!=null);return result.getTransaction().getTransactionId(); }
    private static LedgerAuditDigest base(String action,String tenant,UUID resource,UUID process,String key) { return new LedgerAuditDigest().field(action).field(tenant).field(resource).field(process).field(key); }
    private static LedgerAuditEvidence postingEvidence(String action,String tenant,UUID process,String key,Posting p) {var d=base(action,tenant,p.value().getTransactionId(),process,key);postingFields(d,p);return evidence(action,tenant,"posting",p.value().getTransactionId(),process,key,d,null,null,null);}
    private static void postingFields(LedgerAuditDigest d,Posting p) {
        var t=p.value();var h=p.history().value();d.field(t.getTransactionId()).field(p.claim().getIdempotencyKey()).field(p.claim().getInputFingerprint()).field(t.getAccountId()).field(t.getAccountSpaceId()).field(t.getTransactionType()).field(t.getTotalAmount()).field(t.getCurrency()).field(t.getRelatedTransactionId()).field(t.getRelationType()).field("LOCAL_DATE_TIME_UNKNOWN_ZONE").field(t.getTransactionDate()).field(t.getValueDate()).field(t.getBookingDate()).field(h.getTransactionStatusHistoryId()).field(h.getStatusCode()).field(h.getStatusStartDatetime()).field(p.history().linkedReversal());
        p.legs().stream().sorted(Comparator.comparing(v->v.getTransactionLegId().toString())).forEach(l->d.field(l.getTransactionLegId()).field(l.getAccountId()).field(l.getAccountSpaceId()).field(l.getLegType()).field(l.getAmount()).field(l.getCurrency()).field(l.getValueDate()).field(l.getBookingDate()));
    }
    private static LedgerAuditEvidence evidence(String action,String tenant,String type,UUID resource,UUID process,String key,LedgerAuditDigest digest,Long time,String issuer,String subject) {return new LedgerAuditEvidence(action,tenant,type,resource,process,key,digest.finish(),time,issuer,subject);}
    static String fundingFingerprint(Funding value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("|","FUNDING",value.bankControlAccountId().toString(),value.customerAccountId().toString(),value.amount().stripTrailingZeros().toPlainString(),value.currency()).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
    private static void require(boolean valid) {if(!valid)throw failure();}
    private static IllegalStateException failure() {return new IllegalStateException("Exact durable Ledger audit records required");}
}
