package io.fireflysoftware.core.banking.ledger.web.controllers.funds;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.webflux.context.ReactorSecurityContextAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import reactor.core.publisher.Mono;

class FundsPrincipalCompatibilityTest {
    static Jwt jwt(Object tenant) {
        var builder=Jwt.withTokenValue("controlled-source-token").header("alg","RS256").issuer("https://issuer.example").subject("subject")
                .issuedAt(Instant.now().minusSeconds(1)).expiresAt(Instant.now().plusSeconds(60)).claim("scope","funds:read funds:reserve").claim("funds_accounts",List.of("account"));
        if(tenant!=null)builder.claim("tenant_id",tenant);return builder.build();
    }
    @SuppressWarnings("unchecked")
    static Converter<Jwt,Mono<AbstractAuthenticationToken>> converter()throws Exception {
        try {
            var method=io.fireflysoftware.core.banking.ledger.web.FundsSecurityConfiguration.class.getDeclaredMethod("firstCaptureConverter");method.setAccessible(true);
            return (Converter<Jwt,Mono<AbstractAuthenticationToken>>)method.invoke(null);
        } catch(NoSuchMethodException legacy) { return new ReactiveJwtAuthenticationConverter(); }
    }
    @Test void genuineConverterAndReactorPortSupplyTheSameCompletePrincipalToExistingProjection()throws Exception {
        var token=converter().convert(jwt(" opaque, Ω ")).block();var principal=new ReactorSecurityContextAdapter().currentPrincipal()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(token)).block();assertNotNull(principal);
        var method=java.util.Arrays.stream(FundsController.class.getDeclaredMethods()).filter(m->m.getName().equals("principal")).findFirst().orElseThrow();method.setAccessible(true);
        var projected=assertDoesNotThrow(()->(SecurityPrincipal)method.invoke(null,principal));
        assertSame(principal,projected);assertEquals("https://issuer.example",projected.issuer());assertEquals(" opaque, Ω ",projected.tenantId());
        assertEquals(Set.of("funds:read","funds:reserve"),projected.scopes());assertNotNull(projected.claim("exp",Instant.class));assertEquals(List.of("account"),projected.claims().get("funds_accounts"));
    }
    @Test void missingTenantCannotBeReplacedByPrincipalNameOrOtherClaims()throws Exception {assertThrows(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,()->converter().convert(jwt(null)).block());}
    @Test void ambiguousTenantCannotSelectOneNamespace()throws Exception {assertThrows(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,()->converter().convert(jwt(List.of("one","two"))).block());}
    @Test void enabledScopeMappingRetainsExactLegacySpaceOnlySemantics()throws Exception {
        for(var scope:List.of("funds:reserve,funds:read","funds:reserve\tfunds:read","funds:reserve\nfunds:read")){
            var source=jwt("tenant");var claims=new java.util.HashMap<String,Object>(source.getClaims());claims.put("scope",scope);
            var token=converter().convert(new Jwt(source.getTokenValue(),source.getIssuedAt(),source.getExpiresAt(),source.getHeaders(),claims)).block();
            var principal=new ReactorSecurityContextAdapter().currentPrincipal().contextWrite(ReactiveSecurityContextHolder.withAuthentication(token)).block();
            assertNotNull(principal);assertEquals(Set.of(scope),principal.scopes());assertFalse(principal.hasScope("funds:reserve"));
        }
    }
    @Test void existingJwtProjectionRemainsUnchangedWhenTheFeatureIsDisabled()throws Exception {
        var token=jwt("legacy");var method=java.util.Arrays.stream(FundsController.class.getDeclaredMethods()).filter(m->m.getName().equals("principal")).findFirst().orElseThrow();method.setAccessible(true);
        var projected=(SecurityPrincipal)method.invoke(null,token);assertEquals("legacy",projected.tenantId());assertEquals(Set.of("funds:read","funds:reserve"),projected.scopes());assertEquals(token.getClaims(),projected.claims());
    }
}
