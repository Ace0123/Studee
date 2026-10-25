package com.studymate.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One question. Depending on its type it uses different fields:
 * - FLASHCARD:       questionText + correctAnswer
 * - FILL_BLANK:      questionText (with a blank) + correctAnswer + optional acceptedAnswers
 * - MULTIPLE_CHOICE: questionText + four options + the index of the correct option
 */
public class StudyQuestion {
    public static final int OPTION_COUNT = 4;

    private final int id;
    private final int reviewerId;
    private QuizType type;
    private String questionText;
    private String correctAnswer;
    private String acceptedAnswers;                       // extra accepted answers, one per line
    private final String[] options = {"", "", "", ""};    // multiple choice options A-D
    private int correctOption;                            // 0-3, or -1 when none is chosen

    /** Simple constructor (used for flashcards, and by older code). */
    public StudyQuestion(int id, int reviewerId, String questionText, String correctAnswer) {
        this(id, reviewerId, QuizType.FLASHCARD, questionText, correctAnswer, "", null, -1);
    }

    public StudyQuestion(int id, int reviewerId, QuizType type, String questionText, String correctAnswer,
                         String acceptedAnswers, String[] optionValues, int correctOption) {
        this.id = id;
        this.reviewerId = reviewerId;
        this.type = type == null ? QuizType.FLASHCARD : type;
        this.questionText = questionText == null ? "" : questionText;
        this.correctAnswer = correctAnswer == null ? "" : correctAnswer;
        this.acceptedAnswers = acceptedAnswers == null ? "" : acceptedAnswers;
        setOptions(optionValues);
        setCorrectOption(correctOption);
    }

    public int getId() { return id; }
    public int getReviewerId() { return reviewerId; }
    public QuizType getType() { return type; }
    public void setType(QuizType type) { this.type = type == null ? QuizType.FLASHCARD : type; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText == null ? "" : questionText; }

    /** For multiple choice this is the text of the correct option; otherwise the typed correct answer. */
    public String getCorrectAnswer() {
        if (type == QuizType.MULTIPLE_CHOICE) {
            return correctOption >= 0 && correctOption < OPTION_COUNT ? options[correctOption] : "";
        }
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer == null ? "" : correctAnswer; }

    public String getAcceptedAnswers() { return acceptedAnswers; }
    public void setAcceptedAnswers(String acceptedAnswers) { this.acceptedAnswers = acceptedAnswers == null ? "" : acceptedAnswers; }

    public String getOption(int index) { return options[index]; }
    public String[] getOptions() { return options.clone(); }

    public void setOptions(String[] values) {
        for (int i = 0; i < OPTION_COUNT; i++) {
            String value = values != null && i < values.length && values[i] != null ? values[i].trim() : "";
            options[i] = value;
        }
    }

    public int getCorrectOption() { return correctOption; }
    public void setCorrectOption(int index) { correctOption = index >= 0 && index < OPTION_COUNT ? index : -1; }

    /** Extra accepted answers as a list (blank lines removed). */
    public List<String> getAcceptedAnswerList() {
        List<String> result = new ArrayList<>();
        for (String line : acceptedAnswers.split("\\R")) {
            if (!line.isBlank()) {
                result.add(line.trim());
            }
        }
        return result;
    }

    /** Every answer that counts as correct for fill-in-the-blank: the main answer plus the alternatives. */
    public List<String> getAllAcceptableAnswers() {
        List<String> result = new ArrayList<>();
        if (!correctAnswer.isBlank()) {
            result.add(correctAnswer.trim());
        }
        result.addAll(getAcceptedAnswerList());
        return result;
    }

    /** Four non-empty, different options and one of them chosen as correct. */
    public boolean isValidMultipleChoice() {
        List<String> seen = new ArrayList<>();
        for (String option : options) {
            if (option.isBlank()) {
                return false;
            }
            String normalized = option.trim().toLowerCase(Locale.ROOT);
            if (seen.contains(normalized)) {
                return false;
            }
            seen.add(normalized);
        }
        return correctOption >= 0 && correctOption < OPTION_COUNT;
    }

    /** True when the question has everything needed to be studied. */
    public boolean hasAnswer() {
        if (type == QuizType.MULTIPLE_CHOICE) {
            return isValidMultipleChoice();
        }
        return !correctAnswer.isBlank();
    }
}
