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

No successful capture, native coupling, current authority or time provider is
supplied. Required providers must independently verify current authenticated actor,
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
