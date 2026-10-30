/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.models.repositories.funds;

import io.fireflysoftware.core.banking.ledger.models.entities.funds.FundsAccount;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.FundsCompletedReserveWitness;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import reactor.core.publisher.Mono;

/** Constructed only from the original owning template; excluded from default repository activation. */
@NoRepositoryBean
public interface FundsReserveCompletedWitnessRepository extends Repository<FundsAccount, UUID> {
    @Query("SELECT * FROM public.fnd_require_completed_reserve(:tenant,:operation,:reservation,:source,:destination,:canonical,:digest,:fingerprint,:issuer,:subject,:until)")
    Mono<FundsCompletedReserveWitness> requireCompletedReserve(String tenant, UUID operation,
            UUID reservation, UUID source, UUID destination, String canonical, String digest,
            String fingerprint, String issuer, String subject, OffsetDateTime until);
}
