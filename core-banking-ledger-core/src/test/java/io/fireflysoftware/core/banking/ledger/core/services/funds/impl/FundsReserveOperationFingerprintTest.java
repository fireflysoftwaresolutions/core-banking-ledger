package io.fireflysoftware.core.banking.ledger.core.services.funds.impl;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts.Reserve;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FundsReserveOperationFingerprintTest {
    @ParameterizedTest @CsvSource({"1.23,null,aaeb15d3ca1d0a24fbcf4564083a5b4d4d51187c37faae3fc9062127891d9661",
        "1.23,1700000300000,82665240c2f66265a80dc8405aec603882f775fdf292dcba06e6d96079973252",
        "1.2300,null,aaeb15d3ca1d0a24fbcf4564083a5b4d4d51187c37faae3fc9062127891d9661",
        "1.2300,1700000300000,82665240c2f66265a80dc8405aec603882f775fdf292dcba06e6d96079973252",
        "1E+2,null,f129acbbf08e842bd9cc091faa6493a1dcc7730969169ad6275c234230f7cf37",
        "1E+2,1700000300000,14689895aa8bdcc4156db8ff4931e0a1cad24ef9ec65bc976452b08eef72b897",
        "100.0000,null,f129acbbf08e842bd9cc091faa6493a1dcc7730969169ad6275c234230f7cf37",
        "100.0000,1700000300000,14689895aa8bdcc4156db8ff4931e0a1cad24ef9ec65bc976452b08eef72b897"})
    void originalFingerprintRetainsItsKnownProtocolVectors(String amount,String deadline,String expected){
        var input=new Reserve(new UUID(0,1),new UUID(0,2),new UUID(0,3),new BigDecimal(amount),"USD",300,deadline.equals("null")?null:Long.valueOf(deadline));
        assertEquals(expected,FundsMoneyServiceImpl.reservationFingerprint(input));
        assertEquals(expected,io.fireflysoftware.core.banking.ledger.core.services.funds.FundsReceiptCodec.reserveOperationFingerprint(input));
        assertEquals(expected,io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec.reserveOperationFingerprint(input));
    }
}
