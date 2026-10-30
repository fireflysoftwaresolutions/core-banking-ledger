package io.fireflysoftware.core.banking.ledger.core.services.funds;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsContracts;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.UUID;

class FundsReceiptCodecCompatibilityTest {
    private static final String PORTABLE =
            "io.fireflysoftware.core.banking.ledger.interfaces.protocol.receipt.FundsReceiptCodec";

    @Test
    void publicCodecIsUsableWithInterfacesAndJdkOnly() throws Exception {
        URL interfaces = FundsContracts.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new URLClassLoader(new URL[] {interfaces}, ClassLoader.getPlatformClassLoader())) {
            assertThatCode(() -> Class.forName(PORTABLE, true, loader)).doesNotThrowAnyException();
            var codec = Class.forName(PORTABLE, true, loader);
            assertThatThrownBy(() -> loader.loadClass(FundsReceiptCodec.class.getName()))
                    .isInstanceOf(ClassNotFoundException.class);
            assertThatThrownBy(() -> loader.loadClass("org.springframework.context.ApplicationContext"))
                    .isInstanceOf(ClassNotFoundException.class);
            var reserve = loader.loadClass(FundsContracts.Reserve.class.getName());
            var intent = reserve.getConstructor(UUID.class, UUID.class, UUID.class,
                            BigDecimal.class, String.class, int.class, Long.class)
                    .newInstance(new UUID(0, 1), new UUID(0, 2), new UUID(0, 3),
                            new BigDecimal("1.2300"), "USD", 300, null);
            var canonical = codec.getMethod("canonicalIntent", String.class, reserve)
                    .invoke(null, "owned", intent);
            assertThat(canonical.getClass().getMethod("digest").invoke(canonical))
                    .isEqualTo("f3fc4bd1a623596a79808351aec8014e2ac59380006ec33dcc616bbc51bb0f0e");
        }
    }

    @Test
    void coreCompatibilityFacadeRetainsEveryPublicDescriptorAndEncoding() throws Exception {
        assertThatCode(() -> Class.forName(PORTABLE)).doesNotThrowAnyException();
        var portable = Class.forName(PORTABLE);
        assertThat(methods(FundsReceiptCodec.class)).containsExactly(methods(portable));
        for (String name : new String[] {"INTENT_ENCODING", "ACCEPTED_ENCODING", "RECEIPT_ENCODING"}) {
            assertThat(FundsReceiptCodec.class.getField(name).get(null))
                    .isEqualTo(portable.getField(name).get(null));
        }
    }

    private static String[] methods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()))
                .map(FundsReceiptCodecCompatibilityTest::descriptor).sorted().toArray(String[]::new);
    }

    private static String descriptor(Method method) {
        return method.getName() + Arrays.toString(method.getParameterTypes()) + method.getReturnType().getName();
    }
}
