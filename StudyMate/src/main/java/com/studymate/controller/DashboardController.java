package com.studymate.controller;

import com.studymate.StudyMateApp;
import com.studymate.model.Reviewer;
import com.studymate.model.StudySession;
import com.studymate.service.QuestionDatabase;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** The Dashboard screen: statistics, progress, recent reviewers and quick actions. */
public class DashboardController {
    private final StudyMateApp app;
    private final ScrollPane view;
    private final Label reviewerCountLabel = UiHelper.label("0", "stat-value");
    private final Label questionCountLabel = UiHelper.label("0", "stat-value");
    private final Label sessionCountLabel = UiHelper.label("0", "stat-value");
    private final Label averageLabel = UiHelper.label("-", "stat-value");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label progressText = UiHelper.label("", "subtitle-label");
    private final Label readyText = UiHelper.label("", "subtitle-label");
    private final VBox recentReviewersBox = new VBox(10);
    private final VBox recentSessionsBox = new VBox(8);

    public DashboardController(StudyMateApp app) {
        this.app = app;

        HBox stats = new HBox(16,
                UiHelper.statCard("Reviewers", reviewerCountLabel),
                UiHelper.statCard("Questions", questionCountLabel),
                UiHelper.statCard("Completed sessions", sessionCountLabel),
                UiHelper.statCard("Average score", averageLabel));

        Button importButton = UiHelper.button("Import PDF", "primary-button");
        Button createButton = UiHelper.button("Create Reviewer", "secondary-button");
        Button studyButton = UiHelper.button("Start Studying", "accent-button");
        importButton.setOnAction(e -> importPdf());
        createButton.setOnAction(e -> createReviewer());
        studyButton.setOnAction(e -> app.showStudy(null));
        FlowPane actions = new FlowPane(12, 12, importButton, createButton, studyButton);

        progressBar.setMaxWidth(Double.MAX_VALUE);
        VBox progressCard = new VBox(10, UiHelper.label("Overall study progress", "section-title"),
                progressBar, progressText, readyText);
        progressCard.getStyleClass().add("card");

        Button allReviewers = UiHelper.smallButton("All reviewers", "secondary-button");
        allReviewers.setOnAction(e -> app.showReviewers());
        VBox reviewersCard = new VBox(12, cardHeader("Recent reviewers", allReviewers), recentReviewersBox);
        reviewersCard.getStyleClass().add("card");

        Button allHistory = UiHelper.smallButton("Full history", "secondary-button");
        allHistory.setOnAction(e -> app.showHistory());
        VBox sessionsCard = new VBox(12, cardHeader("Recent study sessions", allHistory), recentSessionsBox);
        sessionsCard.getStyleClass().add("card");

        VBox content = new VBox(22, UiHelper.pageHeader("Dashboard",
                "Import a PDF, fix the questions, then study in the mode you prefer."),
                stats, actions, progressCard, reviewersCard, sessionsCard);
        UiHelper.padScreen(content);
        view = UiHelper.scrollable(content);
    }

    private HBox cardHeader(String title, Button button) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10, UiHelper.label(title, "section-title"), spacer, button);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    public Node getView() { return view; }

    public void refresh() {
        QuestionDatabase db = app.getDatabase();
        int questions = db.getTotalQuestionCount();
        int ready = db.getReadyQuestionCount();
        int sessions = db.getSessionCount();
        double average = sessions == 0 ? 0 : db.getAverageScore();

        reviewerCountLabel.setText(String.valueOf(db.getReviewerCount()));
        questionCountLabel.setText(String.valueOf(questions));
        sessionCountLabel.setText(String.valueOf(sessions));
        averageLabel.setText(sessions == 0 ? "-" : String.format("%.1f%%", average));

        progressBar.setProgress(average / 100.0);
        progressText.setText(sessions == 0
                ? "No sessions completed yet. Your average score will appear here after your first session."
                : "Average score " + String.format("%.1f%%", average) + " over " + sessions + " completed session(s).");
        readyText.setText(questions == 0 ? "" : ready + " of " + questions
                + " question(s) are complete and ready to study.");

        // recent reviewers: most recently studied first, never studied ones after them (newest first)
        List<Reviewer> reviewers = new ArrayList<>(db.getAllReviewers());
        reviewers.sort(Comparator.comparing((Reviewer r) -> r.getLastStudied() == null ? "" : r.getLastStudied())
                .thenComparingInt(Reviewer::getId).reversed());
        recentReviewersBox.getChildren().clear();
        if (reviewers.isEmpty()) {
            recentReviewersBox.getChildren().add(UiHelper.label(
                    "No reviewers yet. Use \"Import PDF\" or \"Create Reviewer\" above to start.", "subtitle-label"));
        }
        for (Reviewer reviewer : reviewers.subList(0, Math.min(4, reviewers.size()))) {
            Label name = UiHelper.label(reviewer.getTitle(), "section-title");
            Label info = UiHelper.label(reviewer.getQuestionCount() + " question(s)  |  "
                    + lastStudiedText(reviewer), "subtitle-label");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button study = UiHelper.smallButton("Study", "primary-button");
            Button open = UiHelper.smallButton("Open", "secondary-button");
            study.setOnAction(e -> app.showStudy(reviewer));
            open.setOnAction(e -> app.showEditor(reviewer));
            HBox row = new HBox(10, new VBox(2, name, info), spacer, open, study);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("row-card");
            recentReviewersBox.getChildren().add(row);
        }

        recentSessionsBox.getChildren().clear();
        List<StudySession> recent = db.getRecentSessions(5);
        if (recent.isEmpty()) {
            recentSessionsBox.getChildren().add(UiHelper.label(
                    "No study sessions yet. Finish a session in Study Mode and it will show up here.", "subtitle-label"));
        }
        for (StudySession session : recent) {
            String line = session.getReviewerTitle() + "  |  " + session.getQuizType().getLabel() + "  |  "
                    + String.format("%.1f%%", session.getPercentage()) + " ("
                    + session.getCorrectCount() + "/" + session.getTotalAnswered() + ")  |  " + session.getCompletedAt();
            recentSessionsBox.getChildren().add(UiHelper.label(line, "subtitle-label"));
        }
    }

    static String lastStudiedText(Reviewer reviewer) {
        return reviewer.getLastStudied() == null ? "Never studied" : "Last studied " + reviewer.getLastStudied();
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
