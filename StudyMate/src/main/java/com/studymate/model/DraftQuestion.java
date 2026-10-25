package com.studymate.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/** A question generated from a PDF that has NOT been saved yet (shown in the import preview). */
public class DraftQuestion {
    private String question;
    private final String sourceSentence;   // the PDF sentence it came from
    private final String suggestedAnswer;  // a guess only - never trust it blindly
    private final BooleanProperty selected = new SimpleBooleanProperty(true); // checkbox state

    public DraftQuestion(String question, String sourceSentence, String suggestedAnswer) {
        this.question = question;
        this.sourceSentence = sourceSentence;
        this.suggestedAnswer = suggestedAnswer;
    }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getSourceSentence() { return sourceSentence; }
    public String getSuggestedAnswer() { return suggestedAnswer; }
    public boolean isSelected() { return selected.get(); }
    public void setSelected(boolean value) { selected.set(value); }
    public BooleanProperty selectedProperty() { return selected; }
}
