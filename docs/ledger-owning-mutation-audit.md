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

Existing controllers and the expiry worker still call their original interfaces.
They are not silently rerouted. The expiry worker completes its bounded candidate
list transaction before invoking each original money action; it has separate
per-item atomicity. Its next adoption must preserve that ordering, use an owning
read boundary for selection and retain the original budget through every per-item
bridge `action` call. Wrapping the whole batch in one transaction would change
existing atomicity. HTTP/default application activation, scheduled expiry adoption,
current owner/native coupling, native rollback/replay/restart, original producer/SDK,
coordinated releases and financial/DORA acceptance remain open.

Ordinary source qualification verifies the complete existing core suite, actual
typed repository/Spring service construction, preserved original REQUIRED advice,
a root-only single physical commit, late capture and actual shared-current-authority
refusal with one physical rollback, exhausted pre-I/O budget refusal, distinct graph
refusal and missing provider construction refusal. Controlled repository effects
and a controlled enclosing caller in the advice regression are not durable funds
or successful audit evidence. No native database execution is included.
