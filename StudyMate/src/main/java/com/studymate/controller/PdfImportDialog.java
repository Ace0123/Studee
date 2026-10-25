package com.studymate.controller;

import com.studymate.StudyMateApp;
import com.studymate.model.DraftQuestion;
import com.studymate.model.Reviewer;
import com.studymate.model.StudyQuestion;
import com.studymate.service.QuestionDatabase;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** PDF import: choose a file, extract text, preview draft questions, import the ticked ones. */
public class PdfImportDialog {
    private PdfImportDialog() { }

    /** Returns the reviewer the questions were imported into, or null if nothing was imported. */
    public static Reviewer show(StudyMateApp app, Reviewer defaultReviewer) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a PDF reviewer");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        File file = chooser.showOpenDialog(app.getStage());
        if (file == null) {
            return null; // user cancelled
        }

        String text;
        Scene mainScene = app.getStage().getScene();
        mainScene.setCursor(Cursor.WAIT);
        try {
            text = app.getPdfService().extractText(file);
        } catch (IOException e) {
            mainScene.setCursor(Cursor.DEFAULT);
            UiHelper.showError("Could not read the PDF",
                    "The file could not be opened. It may be damaged or password-protected.\n\nDetails: " + e.getMessage());
            return null;
        }
        mainScene.setCursor(Cursor.DEFAULT);

        if (text == null || text.isBlank()) {
            UiHelper.showError("No readable text",
                    "This PDF contains no selectable text. It is probably scanned (a picture of pages).\n\n"
                            + "Scanned PDFs need OCR, which is not part of this version.");
            return null;
        }
        List<DraftQuestion> drafts = app.getPdfService().generateDraftQuestions(text);
        if (drafts.isEmpty()) {
            UiHelper.showInfo("No questions found",
                    "The text was read, but no sentences suitable for simple question rules were found.\n"
                            + "You can still add questions manually in the Question Editor.");
            return null;
        }
        return showPreview(app, file, drafts, defaultReviewer);
    }

    private static Reviewer showPreview(StudyMateApp app, File file, List<DraftQuestion> drafts, Reviewer defaultReviewer) {
        QuestionDatabase db = app.getDatabase();
        Reviewer[] importedInto = new Reviewer[1];
        int[] importedCount = new int[1];

        Stage stage = new Stage();
        stage.initOwner(app.getStage());
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Import preview");

        Label title = UiHelper.label("Preview: " + drafts.size() + " draft questions from " + file.getName(), "section-title");
        Label warning = UiHelper.label("These drafts come from simple rules, NOT from AI. Many will be awkward, and the "
                + "program cannot know the correct answers. Untick bad ones, edit the text, and check every answer in the Question Editor.",
                "warning-label");

        // list with checkboxes
        ListView<DraftQuestion> draftList = new ListView<>(FXCollections.observableArrayList(drafts));
        draftList.setCellFactory(CheckBoxListCell.forListView(item -> item.selectedProperty(),
                new StringConverter<DraftQuestion>() {
                    @Override
                    public String toString(DraftQuestion draft) { return draft == null ? "" : draft.getQuestion(); }

                    @Override
                    public DraftQuestion fromString(String string) { return null; }
                }));
        VBox.setVgrow(draftList, Priority.ALWAYS);

        Label countLabel = UiHelper.label("", "subtitle-label");
        Runnable updateCount = () -> {
            int selected = 0;
            for (DraftQuestion draft : drafts) {
                if (draft.isSelected()) selected++;
            }
            countLabel.setText(selected + " of " + drafts.size() + " selected");
        };
        for (DraftQuestion draft : drafts) {
            draft.selectedProperty().addListener((obs, oldValue, newValue) -> updateCount.run());
        }
        updateCount.run();

        Button selectAll = UiHelper.button("Select All", "secondary-button");
        Button selectNone = UiHelper.button("Select None", "secondary-button");
        selectAll.setOnAction(e -> drafts.forEach(d -> d.setSelected(true)));
        selectNone.setOnAction(e -> drafts.forEach(d -> d.setSelected(false)));
        HBox selectRow = new HBox(10, selectAll, selectNone, countLabel);
        selectRow.setAlignment(Pos.CENTER_LEFT);

        // edit the selected draft
        DraftQuestion[] current = new DraftQuestion[1];
        TextArea questionArea = new TextArea();
        questionArea.setWrapText(true);
        questionArea.setPrefRowCount(3);
        questionArea.setPromptText("Click a question above to edit its text here");
        Label sourceLabel = UiHelper.label("", "subtitle-label");
        draftList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> {
            current[0] = selected;
            questionArea.setText(selected == null ? "" : selected.getQuestion());
            sourceLabel.setText(selected == null ? "" : "Source sentence in the PDF: " + selected.getSourceSentence());
        });
        questionArea.textProperty().addListener((obs, oldValue, newValue) -> {
            DraftQuestion draft = current[0];
            if (draft != null && !newValue.equals(draft.getQuestion())) {
                draft.setQuestion(newValue);
                draftList.refresh();
            }
        });

        // where to import
        ComboBox<Reviewer> existingBox = new ComboBox<>(FXCollections.observableArrayList(db.getAllReviewers()));
        existingBox.setPromptText("Choose an existing reviewer");
        existingBox.setPrefWidth(260);
        TextField newTitleField = new TextField();
        newTitleField.setPromptText("...or type a NEW reviewer name");
        newTitleField.setPrefWidth(260);
        if (defaultReviewer != null) {
            for (Reviewer reviewer : existingBox.getItems()) {
                if (reviewer.getId() == defaultReviewer.getId()) {
                    existingBox.setValue(reviewer);
                }
            }
        } else {
            String name = file.getName();
            newTitleField.setText(name.toLowerCase().endsWith(".pdf") ? name.substring(0, name.length() - 4) : name);
        }
        // only one of the two choices can be active at a time
        existingBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                newTitleField.clear();
            }
        });
        newTitleField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue.isBlank()) {
                existingBox.setValue(null);
            }
        });
        HBox targetRow = new HBox(10, UiHelper.label("Import into:", "subtitle-label"), existingBox, newTitleField);
        targetRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox prefillBox = new CheckBox("Pre-fill answers with the text suggested from the PDF (still check every one!)");

        Button importButton = UiHelper.button("Import Selected Questions", "primary-button");
        Button cancelButton = UiHelper.button("Cancel", "secondary-button");
        cancelButton.setOnAction(e -> stage.close());
        importButton.setOnAction(e -> {
            List<DraftQuestion> chosen = new ArrayList<>();
            for (DraftQuestion draft : drafts) {
                if (draft.isSelected() && !draft.getQuestion().isBlank()) {
                    chosen.add(draft);
                }
            }
            if (chosen.isEmpty()) {
                UiHelper.showError("Nothing selected", "Tick at least one question to import.");
                return;
            }
            try {
                Reviewer target;
                String newTitle = newTitleField.getText().trim();
                if (!newTitle.isEmpty()) {
                    target = db.createReviewer(newTitle);
                } else if (existingBox.getValue() != null) {
                    target = existingBox.getValue();
                } else {
                    UiHelper.showError("No reviewer chosen", "Choose an existing reviewer or type a new reviewer name.");
                    return;
                }
                List<StudyQuestion> toSave = new ArrayList<>();
                for (DraftQuestion draft : chosen) {
                    String answer = prefillBox.isSelected() ? draft.getSuggestedAnswer() : "";
                    toSave.add(new StudyQuestion(0, target.getId(), draft.getType(), draft.getQuestion(), answer, "", null, -1));
                }
                importedCount[0] = db.addQuestions(target.getId(), toSave);
                importedInto[0] = target;
            } catch (IllegalArgumentException ex) {
                UiHelper.showError("Cannot import", ex.getMessage());
                return;
            }
            stage.close();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttonRow = new HBox(10, spacer, cancelButton, importButton);

        VBox root = new VBox(12, title, warning, draftList, selectRow,
                UiHelper.label("Edit the selected question:", "subtitle-label"), questionArea, sourceLabel,
                targetRow, prefillBox, buttonRow);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("main-area");

        Scene scene = new Scene(root, 820, 780);
        scene.getStylesheets().add(UiHelper.stylesheetUrl());
        stage.setScene(scene);
        stage.showAndWait();

        if (importedInto[0] != null) {
            UiHelper.showInfo("Import finished",
                    importedCount[0] + " question(s) were added to \"" + importedInto[0].getTitle() + "\".\n\n"
                            + "Next: check each question in the Question Editor and type the correct answers.\n"
                            + "Study Mode will not start while any answer is empty.");
        }
        return importedInto[0];
    }
}
