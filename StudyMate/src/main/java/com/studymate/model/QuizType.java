package com.studymate.model;

/**
 * The three kinds of study. The same enum is used for two things:
 * - the TYPE of a question (what kind of question it was written as), and
 * - the study MODE chosen in Quiz Setup.
 */
public enum QuizType {
    MULTIPLE_CHOICE("Multiple Choice", "Pick the correct answer from four options"),
    FILL_BLANK("Fill in the Blank", "Type the missing word or phrase"),
    FLASHCARD("Flashcards", "Reveal the answer, then grade yourself");

    private final String label;
    private final String description;

    QuizType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }

    /** Combo boxes show the label. */
    @Override
    public String toString() { return label; }

    /** Reads the value stored in the database; unknown or empty text becomes FLASHCARD. */
    public static QuizType fromDatabase(String text) {
        if (text == null) {
            return FLASHCARD;
        }
        try {
            return QuizType.valueOf(text.trim());
        } catch (IllegalArgumentException e) {
            return FLASHCARD;
        }
    }
}
