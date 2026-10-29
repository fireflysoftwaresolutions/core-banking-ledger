/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.core.services.core.impl;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

/** Owner witness encoding; Jafly remains responsible for the canonical event/record chain. */
final class LedgerAuditDigest {
    private final MessageDigest digest;
    private int bytes;
    LedgerAuditDigest() {
        try { digest=MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
        field("firefly.ledger.audit.witness"); field(1);
    }
    LedgerAuditDigest field(Object value) {
        byte[] raw=value==null?null:(value instanceof BigDecimal decimal?decimal.stripTrailingZeros().toPlainString():value.toString()).getBytes(StandardCharsets.UTF_8);
        int size=raw==null?0:raw.length;
        if(size>65536 || bytes>1048576-size-4) throw new IllegalStateException("Bounded Ledger witness required");
        digest.update(ByteBuffer.allocate(4).putInt(raw==null?-1:size).array());
        if(raw!=null) digest.update(raw);
        bytes+=size+4; return this;
    }
    String finish() { return HexFormat.of().formatHex(digest.digest()); }
}
