# Ledger record services: test guide

[Repository guide](../../../../../../../../../../../README.md) · [Module build](../../../../../../../../../../pom.xml)

## Purpose and boundary

This guide maps the authored tests beneath this directory to their source files and gives reproducible commands for reviewing their actual assertions. The test inventory below is a source inventory, not a measured coverage percentage or a record of a successful run.

These package-local repository/mapping tests are distinct from the atomic-posting HTTP/PostgreSQL qualification in the web module. They do not establish account funding, payment-rail finality, cross-service TCC recovery, or production reconciliation. Follow the repository guide for the managed posting lifecycle and its separately qualified cases.

## Prerequisites and fixture ownership

Run commands from the repository root with JDK 25 selected in both `java --version` and `mvn --version`. Resolve the matching parent/framework and reactor dependencies through your authorized Maven configuration. The listed fixture source controls the synthetic IDs, DTOs, mocked dependencies, and expected outcomes; it is not a seed script for a running bank. Read setup and teardown before extending a fixture or executing another module's integration tests.

## Run and inspect

Run one existing class together with its reactor dependencies:

```sh
mvn -B -pl core-banking-ledger-core -am test -DskipOpenApiGen \
  -Dtest=TransactionAttachmentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false
```

To run that module's configured test lifecycle after inspecting all of its fixtures:

```sh
mvn -B -pl core-banking-ledger-core -am test -DskipOpenApiGen
```

`skipOpenApiGen` prevents the parent's fixed-port API-generation startup; it does not skip tests or isolate external resources. `failIfNoSpecifiedTests=false` permits dependency modules without the selected class. Confirm that the selected class appears in `core-banking-ledger-core/target/surefire-reports/` and inspect tests, failures, errors, and skips in the XML. A missing report or skipped case is not a passing qualification. These commands were source-checked for this guide; no new product-test pass is claimed here.

## Source inventory

Each entry lists the existing test method names. Names identify intent; read the linked body to see whether a collaborator, static utility, or the method itself is stubbed.

### [TransactionAttachmentServiceImplTest](attachment/TransactionAttachmentServiceImplTest.java)

- `createAttachment_Success`
- `getAttachment_Success`
- `getAttachment_WrongTransactionId_EmptyResult`
- `listAttachments_Success`
- `listAttachmentsByType_Success`

### [TransactionLineCardServiceImplTest](card/TransactionLineCardServiceImplTest.java)

- `getCardLine_Success`
- `getCardLine_NotFound`
- `createCardLine_Success`
- `createCardLine_Error`
- `updateCardLine_Success`
- `updateCardLine_NotFound`
- `updateCardLine_Error`
- `deleteCardLine_Success`
- `deleteCardLine_NotFound`

### [TransactionServiceImplTest](core/TransactionServiceImplTest.java)

- `createTransaction_Success`
- `getTransaction_Success`
- `getTransaction_NotFound`
- `updateTransaction_Success`
- `updateTransaction_NotFound`
- `deleteTransaction_Success`
- `deleteTransaction_NotFound`
- `filterTransactions_Success`

### [TransactionStatusHistoryServiceImplTest](core/TransactionStatusHistoryServiceImplTest.java)

- `listStatusHistory_Success`
- `createStatusHistory_Success`
- `getStatusHistory_Success`
- `getStatusHistory_NotFound`
- `getStatusHistory_WrongTransactionId`
- `updateStatusHistory_Success`
- `updateStatusHistory_NotFound`
- `updateStatusHistory_WrongTransactionId`
- `deleteStatusHistory_Success`
- `deleteStatusHistory_NotFound`
- `deleteStatusHistory_WrongTransactionId`

### [TransactionLineDepositServiceImplTest](deposit/TransactionLineDepositServiceImplTest.java)

- `getDepositLine_Success`
- `getDepositLine_NotFound`
- `createDepositLine_Success`
- `createDepositLine_Error`
- `updateDepositLine_Success`
- `updateDepositLine_NotFound`
- `deleteDepositLine_Success`
- `deleteDepositLine_NotFound`

### [TransactionLineDirectDebitServiceImplTest](directdebit/TransactionLineDirectDebitServiceImplTest.java)

- `getDirectDebitLine_Success`
- `getDirectDebitLine_NotFound`
- `createDirectDebitLine_Success`
- `updateDirectDebitLine_Success`
- `updateDirectDebitLine_NotFound`
- `deleteDirectDebitLine_Success`
- `deleteDirectDebitLine_NotFound`

### [TransactionLineFeeServiceImplTest](fee/TransactionLineFeeServiceImplTest.java)

- `getFeeLine_Success`
- `getFeeLine_NotFound`
- `createFeeLine_Success`
- `createFeeLine_Error`
- `updateFeeLine_Success`
- `updateFeeLine_NotFound`
- `deleteFeeLine_Success`
- `deleteFeeLine_NotFound`

### [TransactionLineInterestServiceImplTest](interest/TransactionLineInterestServiceImplTest.java)

- `getInterestLine_Success`
- `getInterestLine_NotFound`
- `createInterestLine_Success`
- `createInterestLine_Error`
- `updateInterestLine_Success`
- `updateInterestLine_NotFound`
- `deleteInterestLine_Success`
- `deleteInterestLine_NotFound`

### [TransactionLegServiceImplTest](leg/TransactionLegServiceImplTest.java)

- `createTransactionLeg_Success`
- `getTransactionLeg_Success`
- `getTransactionLeg_WrongTransactionId_EmptyResult`
- `listTransactionLegs_Success`
- `listAccountLegs_Success`
- `listAccountLegsByDateRange_Success`

### [MoneyServiceImplTest](money/MoneyServiceImplTest.java)

- `createMoney_Success`
- `getMoney_Success`
- `listMoney_Success`
- `listMoneyByCurrency_Success`

### [TransactionLineSepaTransferServiceImplTest](sepa/TransactionLineSepaTransferServiceImplTest.java)

- `getSepaTransferLine_Success`
- `getSepaTransferLine_NotFound`
- `createSepaTransferLine_Success`
- `updateSepaTransferLine_Success`
- `updateSepaTransferLine_NotFound`
- `deleteSepaTransferLine_Success`
- `deleteSepaTransferLine_NotFound`

### [TransactionLineStandingOrderServiceImplTest](standingorder/TransactionLineStandingOrderServiceImplTest.java)

- `getStandingOrderLine_Success`
- `getStandingOrderLine_NotFound`
- `createStandingOrderLine_Success`
- `updateStandingOrderLine_Success`
- `updateStandingOrderLine_NotFound`
- `deleteStandingOrderLine_Success`
- `deleteStandingOrderLine_NotFound`

### [StatementServiceImplTest](statement/StatementServiceImplTest.java)

- `getStatement_Success`
- `listAccountStatements_Success`
- `listAccountSpaceStatements_Success`
- `listAccountStatementsByDateRange_Success`
- `listAccountSpaceStatementsByDateRange_Success`

### [TransactionLineTransferServiceImplTest](transfer/TransactionLineTransferServiceImplTest.java)

- `getTransferLine_Success`
- `getTransferLine_NotFound`
- `createTransferLine_Success`
- `createTransferLine_Error`
- `updateTransferLine_Success`
- `updateTransferLine_NotFound`
- `deleteTransferLine_Success`
- `deleteTransferLine_NotFound`

### [TransactionLineWireTransferServiceImplTest](wire/TransactionLineWireTransferServiceImplTest.java)

- `getWireTransferLine_Success`
- `getWireTransferLine_NotFound`
- `createWireTransferLine_Success`
- `updateWireTransferLine_Success`
- `updateWireTransferLine_NotFound`
- `deleteWireTransferLine_Success`
- `deleteWireTransferLine_NotFound`

### [TransactionLineWithdrawalServiceImplTest](withdrawal/TransactionLineWithdrawalServiceImplTest.java)

- `getWithdrawalLine_Success`
- `getWithdrawalLine_NotFound`
- `createWithdrawalLine_Success`
- `createWithdrawalLine_Error`
- `updateWithdrawalLine_Success`
- `updateWithdrawalLine_NotFound`
- `deleteWithdrawalLine_Success`
- `deleteWithdrawalLine_NotFound`

## Actual source example

This excerpt is `createAttachment_Success` from [TransactionAttachmentServiceImplTest](attachment/TransactionAttachmentServiceImplTest.java). Use the surrounding class's imports and fixture setup; this excerpt is not a standalone Java program.

```java
@Test
void createAttachment_Success() {
    // Arrange
    when(mapper.toEntity(any(TransactionAttachmentDTO.class))).thenReturn(attachmentEntity);
    when(repository.save(any(TransactionAttachment.class))).thenReturn(Mono.just(attachmentEntity));
    when(mapper.toDTO(any(TransactionAttachment.class))).thenReturn(attachmentDTO);

    // Act & Assert
    StepVerifier.create(service.createAttachment(transactionId, attachmentDTO))
            .expectNext(attachmentDTO)
            .verifyComplete();

    verify(mapper).toEntity(attachmentDTO);
    verify(repository).save(attachmentEntity);
    verify(mapper).toDTO(attachmentEntity);
}
```

## Interpret and recover

- A Reactor assertion must subscribe and verify termination; constructing a `Mono` alone does not execute it. Match emitted values, empty completion, and errors to the production contract rather than converting every absence to success.
- A repository or mapper mock verifies a local call/result, not committed database state. A stubbed service method or static filter utility does not exercise its implementation. Use real HTTP/SDK/database cases for those boundaries.
- For a failing mock assertion, reread the production branch and fixture data before changing expected values. Do not make a test pass by manufacturing the behavior inside a save callback.
- For dependency resolution or Java errors, verify the actual Maven runtime and matching reactor artifacts. For no matching tests, check the class name and module rather than disabling all tests.
- Coverage requires a configured measurement run and inspected report. Neither a class list nor a successful Maven exit proves every operation, error branch, authorization rule, provider interaction, or customer journey is covered.

## Maintain this guide

Keep the inventory and runnable class selector aligned with the linked sources. Add regression cases for changed behavior, then record the command, fixture boundary, source revision, and reports in the relevant implementation review. Preserve separate evidence for unit behavior, transport contracts, database recovery, provider sandbox behavior, and live-provider acceptance.
