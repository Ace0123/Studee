package com.studymate.service;

import com.studymate.model.QuizType;
import com.studymate.model.StudyQuestion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The logic of ONE study session in any of the three modes (kept in memory; the UI just displays it).
 * Each question can be scored only once: after it has a result it can never be scored again.
 */
public class StudyService {
    private final QuizType mode;
    private final List<StudyQuestion> questions;
    private final String[] typedAnswers;   // text typed by the student (fill in the blank / flashcards)
    private final int[] selectedOptions;   // chosen option for multiple choice, -1 = none yet
    private final boolean[] revealed;      // has the answer been shown (submitted / revealed)?
    private final Boolean[] results;       // null = not scored yet, TRUE = correct, FALSE = incorrect
    private final boolean[] overridden;    // fill in the blank: student counted a "wrong" answer as correct
    private int currentIndex = 0;

    /**
     * @param source  questions of the right type, all with complete answers
     * @param limit   how many questions to study (0 or more than available = all)
     */
    public StudyService(List<StudyQuestion> source, QuizType mode, boolean shuffle, int limit) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("There are no questions to study.");
        }
        for (StudyQuestion question : source) {
            if (!isCompatible(question, mode)) {
                throw new IllegalArgumentException("A question does not fit the " + mode.getLabel() + " mode.");
            }
            if (!question.hasAnswer()) {
                throw new IllegalArgumentException("Every question needs a correct answer before studying.");
            }
        }
        this.mode = mode;
        List<StudyQuestion> list = new ArrayList<>(source);
        if (shuffle) {
            Collections.shuffle(list);
        }
        if (limit > 0 && limit < list.size()) {
            list = new ArrayList<>(list.subList(0, limit));
        }
        questions = list;
        typedAnswers = new String[questions.size()];
        Arrays.fill(typedAnswers, "");
        selectedOptions = new int[questions.size()];
        Arrays.fill(selectedOptions, -1);
        revealed = new boolean[questions.size()];
        results = new Boolean[questions.size()];
        overridden = new boolean[questions.size()];
    }

    // ------------------------------------------------------------ choosing compatible questions

    /** Flashcards can use any question; the other modes only use questions of their own type. */
    public static boolean isCompatible(StudyQuestion question, QuizType mode) {
        return mode == QuizType.FLASHCARD || question.getType() == mode;
    }

    public static List<StudyQuestion> filterCompatible(List<StudyQuestion> all, QuizType mode) {
        List<StudyQuestion> result = new ArrayList<>();
        for (StudyQuestion question : all) {
            if (isCompatible(question, mode)) {
                result.add(question);
            }
        }
        return result;
    }

    /** How many questions are not complete (no correct answer, or invalid multiple choice). */
    public static int countMissingAnswers(List<StudyQuestion> list) {
        int missing = 0;
        for (StudyQuestion question : list) {
            if (!question.hasAnswer()) {
                missing++;
            }
        }
        return missing;
    }

    // ------------------------------------------------------------ checking answers

    /** Lower case, trims, and turns any run of spaces into one space. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** True if the typed text equals the correct answer or any alternative (ignoring case and extra spaces). */
    public static boolean isFillBlankCorrect(String typed, StudyQuestion question) {
        String normalizedTyped = normalize(typed);
        if (normalizedTyped.isEmpty()) {
            return false;
        }
        for (String accepted : question.getAllAcceptableAnswers()) {
            if (normalize(accepted).equals(normalizedTyped)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ current question

    public QuizType getMode() { return mode; }
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

    public int getSelectedOption() { return selectedOptions[currentIndex]; }
    public void selectOption(int index) {
        if (!revealed[currentIndex] && index >= 0 && index < StudyQuestion.OPTION_COUNT) {
            selectedOptions[currentIndex] = index;
        }
    }

    /** True once the answer is visible (after Reveal, or after Submit). */
    public boolean isRevealed() { return revealed[currentIndex]; }

    /** null if not scored yet. */
    public Boolean getCurrentResult() { return results[currentIndex]; }
    public boolean isCurrentOverridden() { return overridden[currentIndex]; }

    // ------------------------------------------------------------ giving answers

    /** Flashcards: show the correct answer. */
    public void reveal() {
        revealed[currentIndex] = true;
    }

    /**
     * Flashcards: the student grades themselves. Returns false (and changes nothing) if the answer was
     * not revealed yet or the question was already scored.
     */
    public boolean mark(boolean wasCorrect) {
        if (mode != QuizType.FLASHCARD || !revealed[currentIndex] || results[currentIndex] != null) {
            return false;
        }
        results[currentIndex] = wasCorrect;
        return true;
    }

    /** Multiple choice: submits the selected option. Returns false if nothing is selected or already submitted. */
    public boolean submitMultipleChoice() {
        if (mode != QuizType.MULTIPLE_CHOICE || results[currentIndex] != null || selectedOptions[currentIndex] < 0) {
            return false;
        }
        revealed[currentIndex] = true;
        results[currentIndex] = selectedOptions[currentIndex] == getCurrentQuestion().getCorrectOption();
        return true;
    }

    /** Fill in the blank: checks the typed answer. Returns false if the question was already submitted. */
    public boolean submitFillBlank() {
        if (mode != QuizType.FILL_BLANK || results[currentIndex] != null) {
            return false;
        }
        revealed[currentIndex] = true;
        results[currentIndex] = isFillBlankCorrect(typedAnswers[currentIndex], getCurrentQuestion());
        return true;
    }

    /**
     * Fill in the blank: the student decides that a "wrong" answer was really right (different wording).
     * This changes the result of the SAME question, so it is never counted twice.
     */
    public boolean countAsCorrect() {
        if (mode != QuizType.FILL_BLANK || results[currentIndex] == null || results[currentIndex] || overridden[currentIndex]) {
            return false;
        }
        results[currentIndex] = true;
        overridden[currentIndex] = true;
        return true;
    }

    /** Jumps to the next question (wrapping around) that is not scored yet. */
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

    // ------------------------------------------------------------ score

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
