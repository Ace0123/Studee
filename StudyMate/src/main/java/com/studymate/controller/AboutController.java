package com.studymate.controller;

import com.studymate.StudyMateApp;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;

/** "About" screen: what the app does, where the data lives and its honest limits. */
public class AboutController {
    private final ScrollPane view;
    private final Label statusLabel = UiHelper.label("", "success-label");

    public AboutController(StudyMateApp app) {
        String databasePath = app.getDatabase().getDatabaseFile().toString();

        Button copyButton = UiHelper.button("Copy database path", "secondary-button");
        copyButton.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(databasePath);
            Clipboard.getSystemClipboard().setContent(content);
            statusLabel.setText("Path copied to the clipboard.");
        });

        VBox about = new VBox(10, UiHelper.label("StudyMate 1.1", "section-title"),
                UiHelper.label("An offline study app: import a PDF reviewer, edit the questions, then study with "
                        + "multiple choice, fill in the blank or flashcards. No internet or AI service is used.", "subtitle-label"));
        about.getStyleClass().add("card");

        VBox data = new VBox(10, UiHelper.label("Your data", "section-title"),
                UiHelper.label("Everything is saved in this file:", "subtitle-label"),
                UiHelper.label(databasePath, "answer-text"), copyButton, statusLabel,
                UiHelper.label("Before the first upgrade from version 1.0, a copy of the old database is kept next to it "
                        + "(studymate-backup-before-v1.1.db).", "subtitle-label"));
        data.getStyleClass().add("card");

        VBox limits = new VBox(10, UiHelper.label("Good to know", "section-title"),
                UiHelper.label("Questions made from a PDF use simple rules, not AI. They are drafts: check every question "
                        + "and type the correct answers yourself.", "subtitle-label"),
                UiHelper.label("Scanned PDFs (pictures of pages) contain no text and cannot be imported.", "subtitle-label"),
                UiHelper.label("Fill in the blank ignores capitalization and extra spaces, and accepts alternative answers. "
                        + "If your wording was different but right, use \"Count it as correct\".", "subtitle-label"));
        limits.getStyleClass().add("card");

        VBox content = new VBox(20, UiHelper.pageHeader("About", "Version, data location and notes."),
                about, data, limits);
        UiHelper.padScreen(content);
        view = UiHelper.scrollable(content);
    }

    public Node getView() { return view; }
}
