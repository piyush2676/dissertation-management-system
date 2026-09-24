package com.dms.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegulationCorpusTest {

    @Test
    void eachPassageCarriesTheHeadingItSitsUnder() {
        List<RegulationCorpus.Passage> passages = RegulationCorpus.split("""
                # 4 Supervision

                ## 4.11 Change of supervisor

                A scholar may request a change of supervisor in writing.

                The committee decides within two weeks.

                5.2 Similarity check
                Similarity must be under ten percent.
                """);

        assertEquals(2, passages.size());
        assertEquals("4.11 Change of supervisor", passages.get(0).heading());
        assertTrue(passages.get(0).text().contains("committee decides"));
        assertEquals("5.2 Similarity check", passages.get(1).heading());
        assertEquals("Similarity must be under ten percent.", passages.get(1).text());
    }

    @Test
    void aLongSectionIsCutIntoSeveralPassagesUnderTheSameHeading() {
        String paragraph = "x".repeat(700);
        List<RegulationCorpus.Passage> passages = RegulationCorpus.split(
                "## 7.1 Evaluation\n\n" + paragraph + "\n\n" + paragraph + "\n\n" + paragraph);

        assertEquals(3, passages.size());
        passages.forEach(p -> assertEquals("7.1 Evaluation", p.heading()));
        assertEquals(List.of(0, 1, 2), passages.stream().map(RegulationCorpus.Passage::index).toList());
    }

    @Test
    void aSentenceThatStartsWithANumberIsNotTakenForAHeading() {
        List<RegulationCorpus.Passage> passages = RegulationCorpus.split("""
                ## 3 Topics

                2 copies of the synopsis are submitted.
                """);

        assertEquals("3 Topics", passages.get(0).heading());
    }

    @Test
    void aNumberedListItemStaysInsideItsSection() {
        List<RegulationCorpus.Passage> passages = RegulationCorpus.split("""
                ## 4.11 Procedure to change the supervisor

                1. Request Submission:
                The scholar submits a written request.

                2. Committee Review:
                The committee reviews it.
                """);

        assertEquals(1, passages.size());
        assertEquals("4.11 Procedure to change the supervisor", passages.get(0).heading());
        assertTrue(passages.get(0).text().contains("Request Submission"));
    }

    @Test
    void aMissingDocumentLeavesTheCorpusUnloaded(@TempDir Path dir) {
        RegulationCorpus corpus = new RegulationCorpus(dir.resolve("absent.md").toString());

        assertFalse(corpus.isLoaded());
        assertEquals(0, corpus.passages().size());
    }

    @Test
    void aPresentDocumentIsReadFromDisk(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("guidelines.md");
        Files.writeString(file, "## 1 Scope\n\nApplies to M.Tech scholars.\n");

        RegulationCorpus corpus = new RegulationCorpus(file.toString());

        assertTrue(corpus.isLoaded());
        assertEquals("1 Scope", corpus.passage(0).heading());
    }
}
