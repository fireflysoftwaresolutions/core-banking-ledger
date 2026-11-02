/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.ledger.web.openapi;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;

/** Complete original controller metadata only; no financial or enrollment execution is accepted here. */
@ActiveProfiles("openapi-gen")
@SpringBootTest(classes=OpenApiGenApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
    properties={"spring.main.web-application-type=reactive","springdoc.api-docs.version=OPENAPI_3_0"})
class CompleteOwnerControllerProducerTest {
    @Autowired ApplicationContext context;
    record Operation(String path,String verb,String id){}
    Set<Operation> operations(JsonNode doc){
        var result=new HashSet<Operation>();var ids=new HashSet<String>();
        doc.path("paths").fields().forEachRemaining(path->path.getValue().fields().forEachRemaining(method->{
            if(Set.of("get","post","put","patch","delete","head","options","trace").contains(method.getKey())){
                String id=method.getValue().path("operationId").asText();assertFalse(id.isBlank());assertTrue(ids.add(id),id);
                assertTrue(result.add(new Operation(path.getKey(),method.getKey(),id)));
            }
        }));assertFalse(result.isEmpty());return result;
    }
    void references(JsonNode root,JsonNode node){
        if(node.isObject())node.fields().forEachRemaining(field->{
            if(field.getKey().equals("$ref")){String value=field.getValue().asText();assertTrue(value.startsWith("#/"),value);assertFalse(root.at(value.substring(1)).isMissingNode(),value);}
            else references(root,field.getValue());
        });else if(node.isArray())node.forEach(value->references(root,value));
    }
    Set<String> sourceControllerInventory()throws Exception {
        var expected=new TreeSet<String>();var readers=new org.springframework.core.type.classreading.CachingMetadataReaderFactory(context);
        Path compiled=Path.of("target/classes").toRealPath();
        for(var resource:context.getResources("classpath*:io/fireflysoftware/core/banking/ledger/web/controllers/**/*.class")){
            var metadata=readers.getMetadataReader(resource);var type=metadata.getClassMetadata();var annotations=metadata.getAnnotationMetadata();
            if(!type.isAbstract()&&!type.isInterface()&&(annotations.hasAnnotation(RestController.class.getName())||annotations.hasMetaAnnotation(RestController.class.getName()))){
                var uri=resource.getURI();assertEquals("file",uri.getScheme());assertTrue(Path.of(uri).toRealPath().startsWith(compiled));
                expected.add(type.getClassName());
            }
        }
        assertFalse(expected.isEmpty());return expected;
    }
    void requireUnusedMetadataFunds(){
        for(Class<?> dependency:List.of(io.fireflysoftware.core.banking.ledger.core.services.funds.FundsAuthority.class,
                io.fireflysoftware.core.banking.ledger.core.services.funds.FundsMoneyService.class,
                io.fireflysoftware.core.banking.ledger.core.services.funds.impl.FundsExpiryWorkerImpl.class,
                io.fireflysoftware.core.banking.ledger.core.services.funds.FundsReconciliationService.class)){
            var details=org.mockito.Mockito.mockingDetails(context.getBean(dependency));assertTrue(details.isMock());assertTrue(details.getInvocations().stream().allMatch(call->call.getMethod().getDeclaringClass()==Object.class));
        }
    }
    @Test void completeCurrentControllersPreserveEveryOriginalOperationBeforeOfficialSdkGeneration()throws Exception {
        requireUnusedMetadataFunds();
        byte[] preimage=Files.readAllBytes(Path.of("../core-banking-ledger-sdk/src/main/resources/openapi.yml"));
        var legacy=new ObjectMapper(new YAMLFactory()).readTree(preimage);var expected=operations(legacy);
        var client=WebTestClient.bindToApplicationContext(context).configureClient().codecs(c->c.defaultCodecs().maxInMemorySize(4194304)).responseTimeout(Duration.ofSeconds(30)).build();
        byte[] actualJson=client.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody().returnResult().getResponseBody();
        byte[] actualYaml=client.get().uri("/v3/api-docs.yaml").exchange().expectStatus().isOk().expectBody().returnResult().getResponseBody();
        assertNotNull(actualJson);assertNotNull(actualYaml);assertTrue(actualJson.length>0&&actualJson.length<=4194304&&actualYaml.length>0&&actualYaml.length<=4194304);
        var document=new ObjectMapper().readTree(actualJson);assertEquals(document,new ObjectMapper(new YAMLFactory()).readTree(actualYaml));
        var actual=operations(document);assertTrue(actual.containsAll(expected),()->"Missing original operations: "+expected.stream().filter(v->!actual.contains(v)).toList());references(document,document);
        var controllers=new TreeSet<String>();for(Object bean:context.getBeansWithAnnotation(RestController.class).values()){
            Class<?> type=ClassUtils.getUserClass(bean);if(!type.getName().startsWith("io.fireflysoftware.core.banking.ledger.web.controllers."))continue;
            assertEquals(Path.of("target/classes").toRealPath(),Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath());controllers.add(type.getName());
        }assertEquals(sourceControllerInventory(),controllers);requireUnusedMetadataFunds();
        Path output=Path.of("target/whole-owner-producer");Files.createDirectories(output);Files.write(output.resolve("openapi.json"),actualJson);Files.write(output.resolve("openapi.yml"),actualYaml);
        var order=Comparator.comparing(Operation::path).thenComparing(Operation::verb).thenComparing(Operation::id);
        Files.writeString(output.resolve("operations.json"),new ObjectMapper().writeValueAsString(Map.of("schema","ACTUAL_COMPLETE_CONTROLLER_PRODUCER_V1","originalSourceSha256",sha(preimage),"actualProducerSha256",sha(actualYaml),"originalOperations",expected.stream().sorted(order).toList(),"actualOperations",actual.stream().sorted(order).toList(),"allOriginalPathVerbIdsPreserved",true,"schemaEquivalenceToPreimageAccepted",false,"actualSourceControllerClasses",controllers,"compiledSourceControllerInventory",sourceControllerInventory(),"financialAuthorityAccepted",false))+"\n");
    }
    private static String sha(byte[] raw)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
}
