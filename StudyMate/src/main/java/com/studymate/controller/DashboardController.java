package com.studymate.controller;

import com.studymate.StudyMateApp;
import com.studymate.model.Reviewer;
import com.studymate.model.StudySession;
import com.studymate.service.QuestionDatabase;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/** The Dashboard screen: statistics, quick buttons and recent study sessions. */
public class DashboardController {
    private final StudyMateApp app;
    private final ScrollPane view = new ScrollPane();
    private final Label reviewerCountLabel = UiHelper.label("0", "stat-value");
    private final Label questionCountLabel = UiHelper.label("0", "stat-value");
    private final Label sessionCountLabel = UiHelper.label("0", "stat-value");
    private final Label averageLabel = UiHelper.label("-", "stat-value");
    private final VBox recentBox = new VBox(8);

    public DashboardController(StudyMateApp app) {
        this.app = app;

        Label title = UiHelper.label("Dashboard", "page-title");
        Label subtitle = UiHelper.label("Import a PDF, fix the questions, then study with flashcards.", "subtitle-label");

        HBox stats = new HBox(16,
                UiHelper.statCard("Reviewers", reviewerCountLabel),
                UiHelper.statCard("Saved questions", questionCountLabel),
                UiHelper.statCard("Completed study sessions", sessionCountLabel),
                UiHelper.statCard("Average score", averageLabel));

        Button importButton = UiHelper.button("Import PDF", "primary-button");
        Button createButton = UiHelper.button("Create Reviewer", "secondary-button");
        Button studyButton = UiHelper.button("Start Studying", "accent-button");
        importButton.setOnAction(e -> importPdf());
        createButton.setOnAction(e -> createReviewer());
        studyButton.setOnAction(e -> app.showStudy(null));
        HBox actions = new HBox(12, importButton, createButton, studyButton);

        VBox recentCard = new VBox(12, UiHelper.label("Recent study sessions", "section-title"), recentBox);
        recentCard.getStyleClass().add("card");

        VBox helpCard = new VBox(8,
                UiHelper.label("How it works", "section-title"),
                UiHelper.label("1. Import a PDF (or create a reviewer and type questions yourself).", "subtitle-label"),
                UiHelper.label("2. Open the Question Editor, fix the draft questions and TYPE THE CORRECT ANSWERS.", "subtitle-label"),
                UiHelper.label("3. Go to Study Mode, reveal answers and mark yourself honestly.", "subtitle-label"));
        helpCard.getStyleClass().add("card");

        VBox content = new VBox(22, title, subtitle, stats, actions, recentCard, helpCard);
        content.setPadding(new Insets(30));
        view.setContent(content);
        view.setFitToWidth(true);
    }

    public Node getView() { return view; }

    public void refresh() {
        QuestionDatabase db = app.getDatabase();
        reviewerCountLabel.setText(String.valueOf(db.getReviewerCount()));
        questionCountLabel.setText(String.valueOf(db.getTotalQuestionCount()));
        int sessions = db.getSessionCount();
        sessionCountLabel.setText(String.valueOf(sessions));
        averageLabel.setText(sessions == 0 ? "-" : String.format("%.1f%%", db.getAverageScore()));

        recentBox.getChildren().clear();
        List<StudySession> recent = db.getRecentSessions(5);
        if (recent.isEmpty()) {
            recentBox.getChildren().add(UiHelper.label("No study sessions yet. Finish a session in Study Mode and it will show up here.", "subtitle-label"));
        }
        for (StudySession session : recent) {
            String line = session.getReviewerTitle() + "  -  " + String.format("%.1f%%", session.getPercentage())
                    + "  (" + session.getCorrectCount() + "/" + session.getTotalAnswered() + " correct)  -  "
                    + session.getCompletedAt();
            recentBox.getChildren().add(UiHelper.label(line, "subtitle-label"));
        }
    }

    private void importPdf() {
        Reviewer imported = PdfImportDialog.show(app, null);
        if (imported != null) {
            app.showEditor(imported);
        }
    }

    private void createReviewer() {
        Optional<String> title = UiHelper.askText("Create Reviewer", "Reviewer title (example: Biology Reviewer)", "");
        if (title.isPresent()) {
            Reviewer reviewer = app.getDatabase().createReviewer(title.get());
            app.showEditor(reviewer);
        }
    }
}
