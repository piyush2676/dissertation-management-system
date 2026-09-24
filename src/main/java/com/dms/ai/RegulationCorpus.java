package com.dms.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The institute guidelines, cut into passages a question can be matched against.
 *
 * <p>The document is the department's, carries its letterhead and a sample
 * scholar's details, and is gitignored for that reason -- so it may simply not be
 * on a given server, and {@link #isLoaded()} is how the page finds out. Nothing
 * here is stored in the database except the passages' embeddings: the text is
 * re-read from the file on each start, so the file stays the one source.
 *
 * <p>A passage is a run of paragraphs under one heading, cut at roughly
 * {@link #TARGET_CHARS} so each is specific enough to cite and long enough to
 * answer from. The heading travels with it: an answer names the section it came
 * from, not an index number.
 */
@Component
@Slf4j
public class RegulationCorpus {

    static final int TARGET_CHARS = 1200;

    /** Markdown headings, and the OCR'd numbered headings the guidelines use ("4.11 Change of supervisor"). */
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^(\\d+(?:\\.\\d+){0,3})\\.?\\s+\\S.{0,90}$");

    public record Passage(int index, String heading, String text) {
        /** What gets embedded: the heading is part of what the passage is about. */
        public String embeddingText() {
            return heading + "\n" + text;
        }
    }

    private final Path file;
    private List<Passage> passages = List.of();

    public RegulationCorpus(@Value("${dms.ai.regulations-file:docs/m.tech_m.tech int._dissertation_guidelines_v3.md}")
                            String file) {
        this.file = Path.of(file);
        reload();
    }

    public boolean isLoaded() {
        return !passages.isEmpty();
    }

    public List<Passage> passages() {
        return passages;
    }

    public Passage passage(int index) {
        return index >= 0 && index < passages.size() ? passages.get(index) : null;
    }

    public String fileName() {
        return file.getFileName().toString();
    }

    final void reload() {
        if (!Files.isRegularFile(file)) {
            log.info("regulations Q&A: {} is not on this server, so the page will say so", file);
            passages = List.of();
            return;
        }
        try {
            passages = split(Files.readString(file, StandardCharsets.UTF_8));
            log.info("regulations Q&A: {} passages from {}", passages.size(), file.getFileName());
        } catch (IOException ex) {
            log.warn("regulations Q&A: could not read {}: {}", file, ex.getMessage());
            passages = List.of();
        }
    }

    /** Splits a document into heading-labelled passages. Package-private for the tests. */
    static List<Passage> split(String document) {
        List<Passage> out = new ArrayList<>();
        String heading = "Introduction";
        StringBuilder current = new StringBuilder();

        for (String paragraph : document.replace("\r\n", "\n").split("\n\\s*\n")) {
            String para = paragraph.strip();
            if (para.isEmpty()) {
                continue;
            }
            String firstLine = para.lines().findFirst().orElse("").strip();
            String found = headingOf(firstLine);
            if (found != null) {
                flush(out, heading, current);
                heading = found;
                para = para.lines().skip(1).reduce("", (a, b) -> a + "\n" + b).strip();
                if (para.isEmpty()) {
                    continue;
                }
            }
            if (current.length() > 0 && current.length() + para.length() > TARGET_CHARS) {
                flush(out, heading, current);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(para);
        }
        flush(out, heading, current);
        return out;
    }

    private static String headingOf(String line) {
        var md = MARKDOWN_HEADING.matcher(line);
        if (md.matches()) {
            return md.group(1).replaceAll("[*_`]", "").strip();
        }
        if (NUMBERED_HEADING.matcher(line).matches() && !line.endsWith(".")) {
            return line;
        }
        return null;
    }

    private static void flush(List<Passage> out, String heading, StringBuilder current) {
        String text = current.toString().strip();
        if (!text.isEmpty()) {
            out.add(new Passage(out.size(), heading, text));
        }
        current.setLength(0);
    }
}
