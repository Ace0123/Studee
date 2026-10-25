package com.studymate.service;

import com.studymate.model.DraftQuestion;
import com.studymate.model.QuizType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads text from PDFs and makes DRAFT questions with simple rules (no AI).
 * Works only for text-based PDFs. Scanned PDFs need OCR, which is not included.
 */
public class PdfService {
    private static final int MAX_DRAFTS = 150;

    // "Subject is/are/means/refers to ... rest of sentence"
    private static final Pattern DEFINITION_PATTERN = Pattern.compile(
            "^(.{3,60}?)\\s+(is defined as|is known as|refers to|means|is|are)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);

    // Sentences starting with these words make bad "What is ...?" questions
    private static final Set<String> BAD_SUBJECT_STARTS = Set.of(
            "it", "this", "that", "these", "those", "they", "he", "she", "we", "there",
            "which", "who", "however", "also", "and", "but", "so", "then", "if", "when");

    /** Extracts all text from a PDF. The document is closed automatically. */
    public String extractText(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /** Splits the text into sentences and turns suitable ones into draft questions. */
    public List<DraftQuestion> generateDraftQuestions(String text) {
        List<DraftQuestion> drafts = new ArrayList<>();
        Set<String> seenQuestions = new HashSet<>();

        // PDF lines are broken in the middle of sentences, so join everything first
        String cleaned = text.replaceAll("\\s+", " ").trim();
        String[] sentences = cleaned.split("(?<=[.!?])\\s+(?=[A-Z0-9])");

        for (String rawSentence : sentences) {
            if (drafts.size() >= MAX_DRAFTS) {
                break;
            }
            String sentence = rawSentence.trim();
            if (sentence.length() < 30 || sentence.length() > 300) {
                continue;
            }
            DraftQuestion draft = makeDefinitionQuestion(sentence);
            if (draft == null) {
                draft = makeFillInTheBlankQuestion(sentence);
            }
            if (draft != null && seenQuestions.add(draft.getQuestion().toLowerCase())) {
                drafts.add(draft);
            }
        }
        return drafts;
    }

    /** Rule 1: "Photosynthesis is the process of ..."  ->  "What is Photosynthesis?" */
    private DraftQuestion makeDefinitionQuestion(String sentence) {
        Matcher matcher = DEFINITION_PATTERN.matcher(sentence);
        if (!matcher.matches()) {
            return null;
        }
        String subject = matcher.group(1).trim();
        String verb = matcher.group(2).toLowerCase();
        String rest = matcher.group(3).trim().replaceAll("[.!?]+$", "").trim();

        String[] subjectWords = subject.split(" ");
        String lowerSubject = " " + subject.toLowerCase() + " ";
        if (subjectWords.length > 6 || subject.contains(",") || subject.contains("(")
                || BAD_SUBJECT_STARTS.contains(subjectWords[0].toLowerCase())
                || lowerSubject.contains(" that ") || lowerSubject.contains(" which ")
                || lowerSubject.contains(" who ") || rest.length() < 3) {
            return null;
        }

        String question;
        if (verb.equals("means")) {
            question = "What does " + subject + " mean?";
        } else if (verb.equals("refers to")) {
            question = "What does " + subject + " refer to?";
        } else if (verb.equals("is known as")) {
            question = "What is " + subject + " known as?";
        } else if (verb.equals("are")) {
            question = "What are " + subject + "?";
        } else {
            question = "What is " + subject + "?";
        }
        return new DraftQuestion(QuizType.FLASHCARD, question, sentence, rest);
    }

    /** Rule 2: blank out the longest word of the sentence. */
    private DraftQuestion makeFillInTheBlankQuestion(String sentence) {
        String[] words = sentence.split(" ");
        if (words.length < 7 || words.length > 35) {
            return null;
        }
        int bestIndex = -1;
        String bestWord = "";
        for (int i = 1; i < words.length; i++) { // skip the first word
            String cleanWord = words[i].replaceAll("[^A-Za-z0-9-]", "");
            if (cleanWord.length() >= 6 && cleanWord.length() > bestWord.length()) {
                bestWord = cleanWord;
                bestIndex = i;
            }
        }
        if (bestIndex == -1) {
            return null;
        }
        words[bestIndex] = words[bestIndex].replace(bestWord, "_____");
        String question = "Fill in the blank: " + String.join(" ", words);
        return new DraftQuestion(QuizType.FILL_BLANK, question, sentence, bestWord);
    }
}
