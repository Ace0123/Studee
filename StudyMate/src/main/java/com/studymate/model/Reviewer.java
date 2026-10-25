package com.studymate.model;

/** A reviewer (a set of questions) such as "Biology Reviewer". */
public class Reviewer {
    private final int id;
    private String title;
    private final int questionCount;
    private final String lastStudied; // date text of the last finished session, or null if never studied

    public Reviewer(int id, String title, int questionCount) {
        this(id, title, questionCount, null);
    }

    public Reviewer(int id, String title, int questionCount, String lastStudied) {
        this.id = id;
        this.title = title;
        this.questionCount = questionCount;
        this.lastStudied = lastStudied;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getQuestionCount() { return questionCount; }
    public String getLastStudied() { return lastStudied; }

    /** ComboBoxes use toString() to show the reviewer. */
    @Override
    public String toString() { return title; }
}
