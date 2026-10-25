package com.studymate.model;

/** One flashcard: a question and its correct answer. */
public class StudyQuestion {
    private final int id;
    private final int reviewerId;
    private String questionText;
    private String correctAnswer;

    public StudyQuestion(int id, int reviewerId, String questionText, String correctAnswer) {
        this.id = id;
        this.reviewerId = reviewerId;
        this.questionText = questionText;
        this.correctAnswer = correctAnswer == null ? "" : correctAnswer;
    }

    public int getId() { return id; }
    public int getReviewerId() { return reviewerId; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer == null ? "" : correctAnswer;
    }

    /** True when a (non-blank) correct answer has been entered. */
    public boolean hasAnswer() { return !correctAnswer.isBlank(); }
}
