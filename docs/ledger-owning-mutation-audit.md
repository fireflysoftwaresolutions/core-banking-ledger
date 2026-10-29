# Explicit owning mutation bridge

`LedgerOwningMutationService` is an explicit outer entrypoint for posting create,
confirm, release and reverse, and for funds fund, reserve and action. Each call
requires the caller's original `R2dbcOperationBudget`, observed before any I/O.
The bridge does not create, reset or renew that budget.

The default-off property is `firefly.ledger.owning-mutations.enabled`. Enabling
requires the original caller-built `OwnedLeaseConnectionFactory`, `DatabaseClient`,
`R2dbcEntityTemplate` and one original `R2dbcTransactionManager`; the graph is
verified by identity. The existing Ledger application repository scan, custom
Spring Data fragments, mapping, client filters, converter, callbacks, service bean
names and `@Transactional` advice are retained. The bridge creates no connection
factory, client, template or transaction manager. The original advised
`ledgerPostingService` and `fundsMoneyService` join the one shared outer root with
their existing REQUIRED propagation. A preexisting generic Spring transaction
cannot be promoted to an owning root.

The complete existing money mutation, including its nested posting, account,
reservation, operation, receipt, event and outbox effects, finishes provisionally
inside that same root before capture. Required `LedgerMutationAuditCapture`
provides the exact current owner-authorized event and native business/audit pairing;
the real shared `SpringDataTransactionalAuditAppender` uses the same graph and
scope. Empty capture, shared authority, append or pairing, mismatched event identity,
late failure or exhausted original budget refuses and prevents commit. Results
are emitted only after acknowledged commit, complete boundary and complete cleanup.
The required `LedgerMutationTime` contains the same Clock and monotonic supplier
used to create the original budget and the shared native caps.

No current authority, native anchor/function implementation or time provider is
supplied. The additive record/capture adapter below still requires those real providers.
Required providers must independently verify current authenticated actor,
exact String platform tenant, qualified UUID resource/legal-entity tenant, exact
operation/replay evidence and original owner authority. Native pairing must enforce
business/audit completeness and append-only integrity in the existing transaction.
The source bridge is not an admission or a financial readiness claim.

`LedgerOwningExpiryService` is a separate explicit entrypoint under the same
default-off gate. It selects one bounded candidate page inside an actual owning
root, completes that read transaction, then calls the owning mutation `action`
entrypoint sequentially for each selected reservation. Every item receives the
identical caller-supplied original budget; neither selection nor an item can
renew it. The original budget must still be live when the aggregate result is
returned. A later refusal can withhold the batch result after earlier separately
committed items; this API does not promise an atomic batch.

The extracted `FundsExpiryCandidateService` retains the original database clock,
HELD-state predicate, tenant filter, stable ordering and page-zero limit of one
to 100. It does not add transaction advice: the original worker retains its
original transaction operator, constructor and bean, and the explicit owning
entrypoint supplies the owning selection root. Existing controllers and the
legacy worker are not silently rerouted.

The owning action bridge distinguishes only the original EXPIRE business
`409 RESERVATION_IS_TERMINAL` refusal after observed rollback, zero commit,
no completed success boundary and complete cleanup. Its typed
`LedgerTerminalExpiryException` binds the reservation and deterministic action
identity. Expiry can skip that exact refusal and continue to the next item.
Other 409 responses, audit-layer errors with identical text, other action types,
incomplete cleanup, expired budgets and unknown outcomes remain errors. Work
failure capture is local to each subscription. The marker records technical
rollback observations; it does not establish business no-effect or financial
acceptance.

HTTP/default application and scheduled expiry activation, genuine original
caller budget provenance, current owner/native coupling, native rollback/replay/
restart, original producer/SDK, coordinated releases and financial/DORA acceptance
remain open.

Ordinary source qualification verifies the complete existing core suite, actual
typed repository/Spring service construction, preserved original REQUIRED advice,
a root-only single physical commit, late capture and actual shared-current-authority
refusal with one physical rollback, exhausted pre-I/O budget refusal, distinct graph
refusal and missing provider construction refusal. Controlled repository effects
and a controlled enclosing caller in the advice regression are not durable funds
or successful audit evidence. No native database execution is included.

The expiry source controls also verify selection completion before each separate
item root, original budget identity and exhaustion across items and final
response, deterministic action IDs, narrow terminal handling, failed cleanup,
per-subscription error isolation, legacy nonempty sequencing and invalid-input
refusal before its original transaction operator. The loop controls use a
controlled selector and business caller with real Spring REQUIRED advice and
network-free physical-driver counters. They are not native selector, audit,
authority or money-movement acceptance.


## Durable record capture prerequisite

The additional default-off `firefly.ledger.audit-capture.enabled` property enables
`LedgerAuditRecordService` and `LedgerMutationAuditCaptureImpl` only together with
the existing owning-mutations gate. It preserves the original resource graph and
advised services. A direct security-spi dependency uses Jafly's actual
`SecurityContextPort`; this change supplies no principal or authority provider.
Missing `LedgerAuditAdmission` prevents construction before connection acquisition.

The typed record service reads each of the seven provisional mutations on the
original opaque scope. It uses bounded Spring Data reads of actual managed
posting headers, claims, balanced legs and selected history, composite funds
operations/actions, original accepted-reserve canonicals, monetary receipts and
funding events. Canonical receipt hashes, original reversal links, tenant,
command, resource and account identities must agree. A successful DTO or current
status alone cannot produce a witness. Legacy local date/time values retain the
explicit unknown-time-zone marker. Legacy subject-only funding actors retain an
unknown issuer. Neither becomes the audit event actor or a business occurrence
instant. A bounded length-prefixed witness digest binds these records; Jafly's
existing canonicalizer still owns audit event and record hashing.

`LedgerAuditAdmission` must authorize the current executor and supply the qualified
UUID resource tenant, current native authority, and original capture actor/policy.
The String platform tenant is never parsed into a UUID. A currently authorized
retry executor can differ from the original actor. Native `fnd_require_audit_capture`
must validate that current authority and return an **existing** original anchor
matching the exact witness and identities. It must never create a replacement
anchor for a historical mutation. Absent original anchor, including replay, refuses.
The anchor's original capture observation time is used for the capture event;
it is not an inferred historic business occurrence time. Original actor, policy,
event ID, observation time and causation remain unchanged on retry.

The typed `fnd_pair_audit_capture` declaration receives that existing capture nonce
and the actual shared provisional record's event ID, sequence, record hash,
evidence digest and replay flag. Before pairing, the adapter rereads the actual
records, current permission and existing anchor and requires full event equality.
The returned event identity must match. Native windows only narrow the same
original budget. Empty/error/late pairing marks that same root rollback-only.
The `_CAPTURED` event's ACCEPTED outcome describes verified capture metadata;
it does not claim financial acceptance or no-effect.

**Native implementation remains a prerequisite.** This source adds no SQL function,
anchor storage, role, grant, successful authority provider or seed. Both functions
must independently enforce their exact role/transaction/tenant/finite-authority,
existing-anchor and atomic business/audit-completeness contracts before native
adoption can be qualified. A declaration or controlled repository response is not
native proof. Existing shared `AuditAppendAuthority` remains independently required.
No controller, scheduled worker, default application property or financial path is
activated by this change.

Ordinary source controls cover all seven typed mutation variants and four action
kinds, missing/ambiguous durable records, altered canonical digest, bounded leg
reads, missing posting/account mismatch, original actor/time preservation under a
different currently authorized retry executor, genuine Jafly record canonicalization,
replay budget narrowing, absent current principal/admission/original anchor/native
function/pair refusal and late rollback. They use controlled repository/authority
contracts with a real bounded root and no external database or HTTP execution.


The accepted-reserve reader also requires the actual retained original RESERVE
operation for reserve and every action. Its composite identity, source and
destination accounts, reservation identity, kind and original operation fingerprint
must match the accepted intent. Its posting ID remains null, exactly as the
original reservation completion writes it; the reservation row owns the actual
posting identity. A completed reserve cannot carry no-effect closure metadata.
These validated operation fields are included in each reserve/action witness, so
contradictory durable operation scope cannot reuse an unchanged audit anchor.
The original operation fingerprint is shared through `FundsReceiptCodec` and the
existing compatibility facade. The original Money helper delegates without changing
its pipe-joined UTF-8 protocol, decimal normalization, or literal null deadline.
That fingerprint remains distinct from the canonical receipt digest.
