package com.studymate.controller;

import com.studymate.StudyMateApp;
import com.studymate.model.Reviewer;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/** "My Reviewers" screen: create, rename, open, study and delete reviewers. */
public class ReviewerController {
    private final StudyMateApp app;
    private final VBox view = new VBox(16);
    private final ListView<Reviewer> listView = new ListView<>();

    public ReviewerController(StudyMateApp app) {
        this.app = app;
        view.setPadding(new Insets(30));

        Label title = UiHelper.label("My Reviewers", "page-title");
        Label subtitle = UiHelper.label("Each reviewer has its own set of questions. Double-click one to edit its questions.", "subtitle-label");

        listView.setPlaceholder(UiHelper.label("No reviewers yet. Click \"New Reviewer\" or import a PDF.", "subtitle-label"));
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Reviewer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.getTitle() + "   -   " + item.getQuestionCount() + " question(s)");
            }
        });
        listView.setOnMouseClicked(e -> {
            Reviewer selected = listView.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && selected != null) {
                app.showEditor(selected);
            }
        });
        VBox.setVgrow(listView, Priority.ALWAYS);

        Button newButton = UiHelper.button("New Reviewer", "primary-button");
        Button importButton = UiHelper.button("Import PDF", "secondary-button");
        newButton.setOnAction(e -> createReviewer());
        importButton.setOnAction(e -> importPdf());

        Button openButton = UiHelper.button("Edit Questions", "accent-button");
        Button studyButton = UiHelper.button("Study", "primary-button");
        Button renameButton = UiHelper.button("Rename", "secondary-button");
        Button deleteButton = UiHelper.button("Delete", "danger-button");
        openButton.setOnAction(e -> app.showEditor(selected()));
        studyButton.setOnAction(e -> app.showStudy(selected()));
        renameButton.setOnAction(e -> renameSelected());
        deleteButton.setOnAction(e -> deleteSelected());
        for (Button button : List.of(openButton, studyButton, renameButton, deleteButton)) {
            button.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());
        }

        view.getChildren().addAll(title, subtitle, new HBox(12, newButton, importButton), listView,
                new HBox(12, openButton, studyButton, renameButton, deleteButton));
    }

    public Node getView() { return view; }

    private Reviewer selected() { return listView.getSelectionModel().getSelectedItem(); }

    public void refresh() {
        listView.setItems(FXCollections.observableArrayList(app.getDatabase().getAllReviewers()));
    }

    private void createReviewer() {
        Optional<String> title = UiHelper.askText("New Reviewer", "Reviewer title (example: Biology Reviewer)", "");
        if (title.isPresent()) {
            app.getDatabase().createReviewer(title.get());
            refresh();
        }
    }

    private void importPdf() {
        Reviewer imported = PdfImportDialog.show(app, selected());
        if (imported != null) {
            app.showEditor(imported);
        }
    }

    private void renameSelected() {
        Reviewer reviewer = selected();
        Optional<String> title = UiHelper.askText("Rename Reviewer", "New title", reviewer.getTitle());
        if (title.isPresent()) {
            app.getDatabase().renameReviewer(reviewer.getId(), title.get());
            refresh();
        }
    }

    private void deleteSelected() {
        Reviewer reviewer = selected();
        boolean sure = UiHelper.confirm("Delete reviewer",
                "Delete \"" + reviewer.getTitle() + "\" with its " + reviewer.getQuestionCount()
                        + " question(s) and study history?\n\nThis cannot be undone.");
        if (sure) {
            app.getDatabase().deleteReviewer(reviewer.getId());
            refresh();
        }
    }
}
