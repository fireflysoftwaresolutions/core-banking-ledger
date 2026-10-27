package io.fireflysoftware.core.banking.ledger.core.architecture;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class LedgerArchitectureTest {
    private Path root() {
        return Path.of(System.getProperty("basedir")).getParent();
    }

    @Test
    void sourceHasNoExactVersionDirectoryOrPackageSegments() throws Exception {
        try (var files = Files.walk(root())) {
            var bad =
                    files.filter(Files::isRegularFile)
                            .filter(p -> p.toString().contains("/src/"))
                            .filter(p -> !p.toString().contains("/target/"))
                            .filter(
                                    p ->
                                            p.toString().matches(".*/v[0-9]+/.*")
                                                    || (p.toString().endsWith(".java")
                                                            && containsVersionPackage(p)))
                            .map(root()::relativize)
                            .toList();
            assertThat(bad).isEmpty();
        }
    }

    private boolean containsVersionPackage(Path file) {
        try {
            return Files.readString(file)
                    .lines()
                    .anyMatch(
                            line ->
                                    line.matches(
                                            "\\s*(package|import)"
                                                + " io\\.fireflysoftware\\.core\\.banking\\.ledger.*\\.v[0-9]+[.;].*"));
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }

    @Test
    void businessServicesContainNoLowLevelDatabaseClientOrSql() throws Exception {
        try (var files = Files.walk(root().resolve("core-banking-ledger-core/src/main/java"))) {
            var bad =
                    files.filter(p -> p.toString().endsWith(".java"))
                            .filter(
                                    p -> {
                                        try {
                                            var text = Files.readString(p);
                                            return text.contains("DatabaseClient")
                                                    || text.matches("(?s).*\\.sql\\s*\\(.*");
                                        } catch (java.io.IOException error) {
                                            throw new java.io.UncheckedIOException(error);
                                        }
                                    })
                            .map(root()::relativize)
                            .toList();
            assertThat(bad).isEmpty();
        }
    }

    @Test
    void repositoriesKeepOnlyExplicitNativePrimitiveQueries() throws Exception {
        var source = root().resolve("core-banking-ledger-models/src/main/java");
        try (var paths = Files.walk(source)) {
            var unexpected =
                    paths.filter(p -> p.toString().endsWith(".java"))
                            .filter(
                                    p ->
                                            !p.getFileName()
                                                    .toString()
                                                    .equals("FundsNativeRepository.java"))
                            .filter(
                                    p -> {
                                        try {
                                            return Files.readString(p).contains("@Query(")
                                                    && !p.getFileName()
                                                            .toString()
                                                            .equals("TransactionRepository.java");
                                        } catch (java.io.IOException error) {
                                            throw new java.io.UncheckedIOException(error);
                                        }
                                    })
                            .map(root()::relativize)
                            .toList();
            assertThat(unexpected).isEmpty();
        }
        String transactions =
                Files.readString(
                        source.resolve(
                                "io/fireflysoftware/core/banking/ledger/models/repositories/core/TransactionRepository.java"));
        assertThat(transactions.lines().filter(line -> line.contains("@Query(")).count())
                .isEqualTo(2);
    }

    @Test
    void businessImplementationsLiveInTheirFunctionalImplPackage() throws Exception {
        try (var files =
                Files.walk(
                        root().resolve(
                                        "core-banking-ledger-core/src/main/java/io/fireflysoftware/core/banking/ledger/core/services"))) {
            assertThat(
                            files.filter(
                                            path ->
                                                    path.getFileName()
                                                            .toString()
                                                            .endsWith("Impl.java"))
                                    .filter(
                                            path ->
                                                    !path.getParent()
                                                            .getFileName()
                                                            .toString()
                                                            .equals("impl"))
                                    .map(root()::relativize)
                                    .toList())
                    .isEmpty();
        }
    }

    @Test
    void financialServiceContractsHaveSeparateImplementations() throws Exception {
        for (String name :
                List.of(
                        "FundsMoneyService",
                        "FundsReconciliationService",
                        "FundsReceiptService",
                        "LedgerPostingService")) {
            String area = name.equals("LedgerPostingService") ? "core" : "funds";
            Class<?> contract =
                    Class.forName(
                            "io.fireflysoftware.core.banking.ledger.core.services."
                                    + area
                                    + "."
                                    + name);
            assertThat(contract.isInterface()).isTrue();
            assertThat(
                            contract.isAssignableFrom(
                                    Class.forName(
                                            contract.getPackageName()
                                                    + ".impl."
                                                    + contract.getSimpleName()
                                                    + "Impl")))
                    .isTrue();
        }
    }
}
