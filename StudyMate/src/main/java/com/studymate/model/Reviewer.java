package com.studymate.model;

/** A reviewer (a set of flashcards) such as "Biology Reviewer". */
public class Reviewer {
    private final int id;
    private String title;
    private final int questionCount;

    public Reviewer(int id, String title, int questionCount) {
        this.id = id;
        this.title = title;
        this.questionCount = questionCount;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getQuestionCount() { return questionCount; }

    /** ComboBoxes use toString() to show the reviewer. */
    @Override
    public String toString() { return title; }
}
