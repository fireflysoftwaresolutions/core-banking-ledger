package io.fireflysoftware.core.banking.ledger.core.services.funds.ownership;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class LedgerFundsReserveWitnessSqlContractTest {
    static Path resources(){Path at=Path.of(System.getProperty("basedir",".")).toAbsolutePath();for(int i=0;i<5&&at!=null;i++,at=at.getParent()){var path=at.resolve("core-banking-ledger-models/src/main/resources");if(Files.isDirectory(path))return path;}throw new AssertionError("Original Ledger model resources required");}
    @Test void firstReadRequiresTheProtectedOriginalCompletionContract()throws Exception {
        var path=resources().resolve("db/optional/funds/install_completed_reserve_witness.sql");assertTrue(Files.isRegularFile(path),"FIRST native completed-witness reader is absent");var sql=Files.readString(path);
        assertTrue(sql.contains("public.fnd_active_command()"));assertTrue(sql.contains("public.fnd_assert_command_complete(c.command_id)"));assertTrue(sql.contains("i.command_id=c.command_id"));
    }
    static String sql()throws Exception{return Files.readString(resources().resolve("db/optional/funds/install_completed_reserve_witness.sql"));}
    @Test void nativeReaderDoesNotCreateOrFinishAnyBusinessOrAuthorityState()throws Exception {
        var text=sql();var body=text.substring(text.indexOf("LANGUAGE plpgsql VOLATILE SECURITY DEFINER"),text.indexOf("END $$;",text.indexOf("LANGUAGE plpgsql VOLATILE SECURITY DEFINER")));
        assertFalse(java.util.regex.Pattern.compile("\\b(INSERT|UPDATE|DELETE|TRUNCATE)\\b").matcher(body).find());assertFalse(body.contains("set_config"));assertFalse(body.contains("gen_random_uuid"));assertFalse(body.contains("fnd_finish_command("));
    }
    @Test void callerCannotSupplyNativeCommandOrPhysicalSessionIdentity()throws Exception {
        var text=sql();var signature=text.substring(text.indexOf("CREATE FUNCTION"),text.indexOf("RETURNS TABLE"));assertFalse(signature.contains("p_command"));assertFalse(signature.contains("p_xid"));assertFalse(signature.contains("p_role"));assertTrue(text.contains("c.created_xid=pg_current_xact_id() AND c.executor_role=session_user"));assertTrue(text.contains("pg_current_xact_id()::text"));assertTrue(text.contains("pg_backend_pid()"));
    }
    @Test void canonicalIntentAndActualFingerprintAreRequiredWithoutInventedProtocol()throws Exception {
        var text=sql();assertTrue(text.contains("public.fnd_intent_bytes(c.tenant_id,c.operation_id"));assertTrue(text.contains("public.fnd_b64(original,4096)"));assertTrue(text.contains("(i.intent_digest COLLATE \"C\")=(p_digest COLLATE \"C\")"));assertTrue(text.contains("(o.fingerprint COLLATE \"C\")=(p_fingerprint COLLATE \"C\")"));assertTrue(text.contains("c.source_account_id=p_source AND c.destination_account_id=p_destination"));
    }
    @Test void nativeHorizonRetainsOriginalLimitsAndChecksAfterCompletion()throws Exception {
        var text=sql();assertTrue(text.contains("least(caller_until,c.token_expires_epoch_millis,c.expires_at_epoch_millis)"));assertTrue(text.contains("caller_until<=observed+5000"));assertTrue(text.lastIndexOf("effective_until>observed")>text.indexOf("public.fnd_assert_command_complete(c.command_id)"));assertTrue(text.contains("again:=public.fnd_active_command()"));
    }
    @Test void optionalInstallationKeepsRuntimePrivateAccessAndMembershipUnchanged()throws Exception {
        var text=sql();assertTrue(text.startsWith("-- Explicit optional installation"));assertTrue(text.contains("BEGIN;"));assertTrue(text.endsWith("COMMIT;\n"));assertTrue(text.contains("TO firefly_funds_receipt_api;"));assertTrue(text.contains("REVOKE CREATE ON SCHEMA public FROM firefly_funds_receipt_guard"));assertFalse(text.contains("GRANT SELECT"));assertFalse(text.contains("GRANT USAGE"));assertFalse(text.contains("GRANT firefly_"));assertTrue(text.contains("FROM PUBLIC;"));assertTrue(text.contains("NOT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls"));
    }
    @Test void originalNativeGuardStillRejectsOwnerAndPolicyRoleMembership()throws Exception {
        var original=Files.readString(resources().resolve("db/migration/V59__Immutable_funds_monetary_receipts.sql"));assertTrue(original.contains("pg_catalog.pg_has_role(principal,'firefly_funds_receipt_guard','MEMBER')"));assertTrue(original.contains("pg_catalog.pg_has_role(principal,'firefly_funds_policy_reader','MEMBER')"));assertTrue(original.contains("pg_catalog.pg_has_role(principal,'firefly_funds_policy_writer','MEMBER')"));assertTrue(original.contains("expected_count:=CASE c.purpose WHEN 'RESERVE' THEN 12"));
    }

}
