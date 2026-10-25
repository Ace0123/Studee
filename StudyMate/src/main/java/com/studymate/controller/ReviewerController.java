package com.studymate.controller;

import com.studymate.StudyMateApp;
import com.studymate.model.Reviewer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/** "My Reviewers" screen: one card per reviewer with Start Studying, Open, Rename and Delete. */
public class ReviewerController {
    private final StudyMateApp app;
    private final ScrollPane view;
    private final FlowPane cardPane = new FlowPane(18, 18);
    private final VBox emptyBox;

    public ReviewerController(StudyMateApp app) {
        this.app = app;

        Button newButton = UiHelper.button("New Reviewer", "primary-button");
        Button importButton = UiHelper.button("Import PDF", "secondary-button");
        newButton.setOnAction(e -> createReviewer());
        importButton.setOnAction(e -> importPdf());

        emptyBox = UiHelper.emptyState("No reviewers yet",
                "A reviewer is a set of questions for one subject, such as \"Biology Reviewer\".\n"
                        + "Create one, or import a PDF to get draft questions.");
        emptyBox.getChildren().add(new HBox(10,
                makeButton("New Reviewer", "primary-button", this::createReviewer),
                makeButton("Import PDF", "secondary-button", this::importPdf)));

        VBox content = new VBox(20, UiHelper.pageHeader("My Reviewers",
                "Each reviewer has its own set of questions."),
                new HBox(12, newButton, importButton), emptyBox, cardPane);
        UiHelper.padScreen(content);
        view = UiHelper.scrollable(content);
    }

    private Button makeButton(String text, String style, Runnable action) {
        Button button = UiHelper.button(text, style);
        button.setOnAction(e -> action.run());
        return button;
    }

    public Node getView() { return view; }

    public void refresh() {
        List<Reviewer> reviewers = app.getDatabase().getAllReviewers();
        cardPane.getChildren().clear();
        emptyBox.setVisible(reviewers.isEmpty());
        emptyBox.setManaged(reviewers.isEmpty());
        for (Reviewer reviewer : reviewers) {
            cardPane.getChildren().add(buildCard(reviewer));
        }
    }

    private VBox buildCard(Reviewer reviewer) {
        Label title = UiHelper.label(reviewer.getTitle(), "section-title");
        Label count = UiHelper.label(reviewer.getQuestionCount() + " question(s)", "subtitle-label");
        Label last = UiHelper.label(DashboardController.lastStudiedText(reviewer), "subtitle-label");

        Button studyButton = UiHelper.button("Start Studying", "primary-button");
        studyButton.setMaxWidth(Double.MAX_VALUE);
        studyButton.setOnAction(e -> app.showStudy(reviewer));
        Button openButton = UiHelper.smallButton("Open", "accent-button");
        Button renameButton = UiHelper.smallButton("Rename", "secondary-button");
        Button deleteButton = UiHelper.smallButton("Delete", "danger-button");
        openButton.setOnAction(e -> app.showEditor(reviewer));
        renameButton.setOnAction(e -> rename(reviewer));
        deleteButton.setOnAction(e -> delete(reviewer));
        HBox smallRow = new HBox(8, openButton, renameButton, deleteButton);
        smallRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, title, count, last, studyButton, smallRow);
        card.setPadding(new Insets(20));
        card.getStyleClass().add("reviewer-card");
        card.setPrefWidth(310);
        card.setMinWidth(260);
        VBox.setMargin(studyButton, new Insets(8, 0, 0, 0));
        return card;
    }

    private void createReviewer() {
        Optional<String> title = UiHelper.askText("New Reviewer", "Reviewer title (example: Biology Reviewer)", "");
        if (title.isPresent()) {
            app.getDatabase().createReviewer(title.get());
            refresh();
        }
    }

    private void importPdf() {
        Reviewer imported = PdfImportDialog.show(app, null);
        if (imported != null) {
            app.showEditor(imported);
        }
    }

    private void rename(Reviewer reviewer) {
        Optional<String> title = UiHelper.askText("Rename Reviewer", "New title", reviewer.getTitle());
        if (title.isPresent()) {
            app.getDatabase().renameReviewer(reviewer.getId(), title.get());
            refresh();
        }
    }

    private void delete(Reviewer reviewer) {
        boolean sure = UiHelper.confirm("Delete reviewer",
                "Delete \"" + reviewer.getTitle() + "\" with its " + reviewer.getQuestionCount()
                        + " question(s) and study history?\n\nThis cannot be undone.");
        if (sure) {
            app.getDatabase().deleteReviewer(reviewer.getId());
            refresh();
        }
    }
}
