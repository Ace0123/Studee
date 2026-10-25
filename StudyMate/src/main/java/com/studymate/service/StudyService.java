package com.studymate.service;

import com.studymate.model.StudyQuestion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The logic of ONE flashcard study session (kept in memory; the UI just displays it). */
public class StudyService {
    private final List<StudyQuestion> questions;
    private final String[] typedAnswers;   // what the student typed for each question
    private final boolean[] revealed;      // has the answer been revealed?
    private final Boolean[] results;       // null = not marked yet, TRUE = correct, FALSE = incorrect
    private int currentIndex = 0;

    public StudyService(List<StudyQuestion> source, boolean shuffle) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("This reviewer has no questions to study.");
        }
        if (countMissingAnswers(source) > 0) {
            throw new IllegalArgumentException("Every question needs a correct answer before studying.");
        }
        questions = new ArrayList<>(source);
        if (shuffle) {
            Collections.shuffle(questions);
        }
        typedAnswers = new String[questions.size()];
        Arrays.fill(typedAnswers, "");
        revealed = new boolean[questions.size()];
        results = new Boolean[questions.size()];
    }

    /** How many questions have an empty correct answer. */
    public static int countMissingAnswers(List<StudyQuestion> list) {
        int missing = 0;
        for (StudyQuestion question : list) {
            if (!question.hasAnswer()) {
                missing++;
            }
        }
        return missing;
    }

    // ---- current question
    public StudyQuestion getCurrentQuestion() { return questions.get(currentIndex); }
    public int getCurrentNumber() { return currentIndex + 1; }
    public int getTotal() { return questions.size(); }
    public boolean hasNext() { return currentIndex < questions.size() - 1; }
    public boolean hasPrevious() { return currentIndex > 0; }
    public void next() { if (hasNext()) currentIndex++; }
    public void previous() { if (hasPrevious()) currentIndex--; }

    public String getTypedAnswer() { return typedAnswers[currentIndex]; }
    public void setTypedAnswer(String text) {
        if (!revealed[currentIndex]) {
            typedAnswers[currentIndex] = text == null ? "" : text;
        }
    }

    public boolean isRevealed() { return revealed[currentIndex]; }
    public void reveal() { revealed[currentIndex] = true; }

    /** null if not marked yet. */
    public Boolean getCurrentResult() { return results[currentIndex]; }

    /**
     * Marks the current question. Returns false (and changes nothing) if the answer
     * was not revealed yet or the question was already marked.
     */
    public boolean mark(boolean wasCorrect) {
        if (!revealed[currentIndex] || results[currentIndex] != null) {
            return false;
        }
        results[currentIndex] = wasCorrect;
        return true;
    }

    /** Jumps to the next question (wrapping around) that is not marked yet. */
    public boolean goToNextUnmarked() {
        for (int step = 1; step <= questions.size(); step++) {
            int index = (currentIndex + step) % questions.size();
            if (results[index] == null) {
                currentIndex = index;
                return true;
            }
        }
        return false;
    }

    // ---- score
    public int getAnsweredCount() {
        int count = 0;
        for (Boolean result : results) {
            if (result != null) count++;
        }
        return count;
    }

    public int getCorrectCount() {
        int count = 0;
        for (Boolean result : results) {
            if (result != null && result) count++;
        }
        return count;
    }

    public int getIncorrectCount() { return getAnsweredCount() - getCorrectCount(); }
    public boolean isAllAnswered() { return getAnsweredCount() == questions.size(); }

    /** Correct / answered * 100 (0 when nothing was answered). */
    public double getPercentage() {
        int answered = getAnsweredCount();
        return answered == 0 ? 0 : getCorrectCount() * 100.0 / answered;
    }

    public List<StudyQuestion> getIncorrectQuestions() {
        List<StudyQuestion> wrong = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            if (results[i] != null && !results[i]) {
                wrong.add(questions.get(i));
            }
        }
        return wrong;
    }
}
