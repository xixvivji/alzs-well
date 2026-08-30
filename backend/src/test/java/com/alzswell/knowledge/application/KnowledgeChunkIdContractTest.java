package com.alzswell.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeChunkIdContractTest {
    private static final ObjectMapper MAPPER=new ObjectMapper();

    @Test
    void matchesEveryCanonicalJsonAndChunkIdInTheSharedContract() throws Exception {
        Path contract=Path.of(System.getProperty("user.dir"),"..","contracts","knowledge",
                "chunk-id-test-vectors.json").normalize();
        assertThat(contract).as("shared chunk ID contract file").isRegularFile();
        JsonNode root=MAPPER.readTree(Files.readString(contract));
        assertThat(root.path("contractVersion").asText()).isEqualTo("1.0.0");
        assertThat(root.path("algorithm").asText()).isEqualTo("NFC_EACH_STRING_THEN_RFC8785_UTF8_SHA256");

        for(JsonNode vector:root.withArray("vectors")) {
            JsonNode input=vector.withArray("input");
            List<String> sectionPath=new ArrayList<>();
            input.get(2).forEach(value->sectionPath.add(value.textValue()));
            KnowledgeChunkId.Result actual=KnowledgeChunkId.compute(input.get(0).textValue(),input.get(1).textValue(),
                    sectionPath,input.get(3).intValue(),input.get(4).textValue(),input.get(5).textValue());
            assertThat(actual.canonicalJson()).as(vector.path("name").asText()+" canonical JSON")
                    .isEqualTo(vector.path("canonicalJson").asText());
            assertThat(actual.chunkId()).as(vector.path("name").asText()+" chunk ID")
                    .isEqualTo(vector.path("expectedChunkId").asText());
        }
    }
}
