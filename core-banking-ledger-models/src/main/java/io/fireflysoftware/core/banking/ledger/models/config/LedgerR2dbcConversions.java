package io.fireflysoftware.core.banking.ledger.models.config;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionTypeEnum;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.spi.Parameter;
import io.r2dbc.spi.Parameters;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;

import java.util.List;
import java.util.Locale;

@Configuration
public class LedgerR2dbcConversions {
    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return R2dbcCustomConversions.of(
                PostgresDialect.INSTANCE,
                List.of(
                        new TransactionTypeWriter(), new TransactionTypeReader(),
                        new TransactionStatusWriter(), new TransactionStatusReader()));
    }

    // The original PostgreSQL enum labels are lowercase; API enum names remain uppercase.
    @WritingConverter
    static class TransactionTypeWriter implements Converter<TransactionTypeEnum, Parameter> {
        public Parameter convert(TransactionTypeEnum source) {
            return Parameters.in(
                    PostgresqlObjectId.UNSPECIFIED, source.name().toLowerCase(Locale.ROOT));
        }
    }

    @ReadingConverter
    static class TransactionTypeReader implements Converter<String, TransactionTypeEnum> {
        public TransactionTypeEnum convert(String source) {
            return TransactionTypeEnum.valueOf(source.toUpperCase(Locale.ROOT));
        }
    }

    @WritingConverter
    static class TransactionStatusWriter implements Converter<TransactionStatusEnum, Parameter> {
        public Parameter convert(TransactionStatusEnum source) {
            return Parameters.in(
                    PostgresqlObjectId.UNSPECIFIED, source.name().toLowerCase(Locale.ROOT));
        }
    }

    @ReadingConverter
    static class TransactionStatusReader implements Converter<String, TransactionStatusEnum> {
        public TransactionStatusEnum convert(String source) {
            return TransactionStatusEnum.valueOf(source.toUpperCase(Locale.ROOT));
        }
    }
}
