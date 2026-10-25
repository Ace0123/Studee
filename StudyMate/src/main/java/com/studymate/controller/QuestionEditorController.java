package com.studymate.controller;

import com.studymate.StudyMateApp;
import com.studymate.model.Reviewer;
import com.studymate.model.StudyQuestion;
import com.studymate.service.QuestionDatabase;
import com.studymate.service.StudyService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/** Question Editor: add, edit, delete and preview questions and type the correct answers. */
public class QuestionEditorController {
    private final StudyMateApp app;
    private final VBox view = new VBox(16);
    private final ComboBox<Reviewer> reviewerCombo = new ComboBox<>();
    private final ListView<StudyQuestion> questionList = new ListView<>();
    private final Label summaryLabel = UiHelper.label("", "subtitle-label");
    private final Label formTitle = UiHelper.label("New question", "section-title");
    private final TextArea questionArea = new TextArea();
    private final TextArea answerArea = new TextArea();
    private final VBox formCard = new VBox(12);

    private StudyQuestion editingQuestion = null; // null = the form is for a NEW question
    private boolean loading = false;              // true while we fill the combo box ourselves

    public QuestionEditorController(StudyMateApp app) {
        this.app = app;
        view.setPadding(new Insets(30));

        Label title = UiHelper.label("Question Editor", "page-title");
        Label subtitle = UiHelper.label("Edit questions and TYPE the correct answers. Study Mode needs every answer filled in.", "subtitle-label");

        // top row
        Button importButton = UiHelper.button("Import PDF", "secondary-button");
        Button studyButton = UiHelper.button("Study This Reviewer", "primary-button");
        importButton.setOnAction(e -> importPdf());
        studyButton.setOnAction(e -> {
            if (reviewerCombo.getValue() == null) {
                UiHelper.showError("No reviewer", "Create or choose a reviewer first.");
            } else {
                app.showStudy(reviewerCombo.getValue());
            }
        });
        reviewerCombo.setPrefWidth(300);
        reviewerCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!loading) {
                loadQuestions();
            }
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(12, UiHelper.label("Reviewer:", "section-title"), reviewerCombo, spacer, importButton, studyButton);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // left: question list
        questionList.setPlaceholder(UiHelper.label("No questions yet.\nAdd one on the right or import a PDF.", "subtitle-label"));
        questionList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(StudyQuestion item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String text = item.getQuestionText().replace("\n", " ");
                    setText((item.hasAnswer() ? "" : "\u26A0 ") + text);
                }
            }
        });
        questionList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> {
            if (selected != null) {
                editingQuestion = selected;
                questionArea.setText(selected.getQuestionText());
                answerArea.setText(selected.getCorrectAnswer());
                formTitle.setText("Edit question");
            }
        });
        VBox.setVgrow(questionList, Priority.ALWAYS);
        VBox leftBox = new VBox(8, UiHelper.label("Questions (\u26A0 = no correct answer yet)", "section-title"), questionList, summaryLabel);
        leftBox.setPrefWidth(420);
        HBox.setHgrow(leftBox, Priority.ALWAYS);

        // right: form
        questionArea.setPromptText("Type the question here...");
        questionArea.setWrapText(true);
        questionArea.setPrefRowCount(5);
        answerArea.setPromptText("Type the CORRECT answer here (you decide, not the program)...");
        answerArea.setWrapText(true);
        answerArea.setPrefRowCount(5);

        Button newButton = UiHelper.button("New Question", "secondary-button");
        Button saveButton = UiHelper.button("Save", "primary-button");
        Button previewButton = UiHelper.button("Preview", "accent-button");
        Button deleteButton = UiHelper.button("Delete", "danger-button");
        newButton.setOnAction(e -> startNewQuestion());
        saveButton.setOnAction(e -> saveQuestion());
        previewButton.setOnAction(e -> previewQuestion());
        deleteButton.setOnAction(e -> deleteQuestion());

        formCard.getChildren().addAll(formTitle,
                UiHelper.label("Question", "subtitle-label"), questionArea,
                UiHelper.label("Correct answer", "subtitle-label"), answerArea,
                new HBox(10, newButton, saveButton, previewButton, deleteButton));
        formCard.getStyleClass().add("card");
        formCard.setPrefWidth(420);
        HBox.setHgrow(formCard, Priority.ALWAYS);

        HBox middle = new HBox(20, leftBox, formCard);
        VBox.setVgrow(middle, Priority.ALWAYS);
        view.getChildren().addAll(title, subtitle, topRow, middle);
    }

    public Node getView() { return view; }

    /** Reloads reviewers + questions. If reviewerToShow is null, keeps the current reviewer. */
    public void refresh(Reviewer reviewerToShow) {
        QuestionDatabase db = app.getDatabase();
        int wantedId = -1;
        if (reviewerToShow != null) {
            wantedId = reviewerToShow.getId();
        } else if (reviewerCombo.getValue() != null) {
            wantedId = reviewerCombo.getValue().getId();
        }

        loading = true;
        List<Reviewer> all = db.getAllReviewers();
        reviewerCombo.setItems(FXCollections.observableArrayList(all));
        Reviewer toSelect = null;
        for (Reviewer reviewer : all) {
            if (reviewer.getId() == wantedId) {
                toSelect = reviewer;
            }
        }
        if (toSelect == null && !all.isEmpty()) {
            toSelect = all.get(0);
        }
        reviewerCombo.setValue(toSelect);
        loading = false;
        loadQuestions();
    }

    private void loadQuestions() {
        Reviewer reviewer = reviewerCombo.getValue();
        clearForm();
        formCard.setDisable(reviewer == null);
        if (reviewer == null) {
            questionList.setItems(FXCollections.observableArrayList());
            summaryLabel.setText("Create a reviewer first (My Reviewers, or import a PDF).");
            return;
        }
        List<StudyQuestion> questions = app.getDatabase().getQuestions(reviewer.getId());
        questionList.setItems(FXCollections.observableArrayList(questions));
        int missing = StudyService.countMissingAnswers(questions);
        summaryLabel.setText(questions.size() + " question(s), " + missing + " without a correct answer.");
    }

    private void clearForm() {
        editingQuestion = null;
        questionArea.clear();
        answerArea.clear();
        formTitle.setText("New question");
    }

    private void startNewQuestion() {
        questionList.getSelectionModel().clearSelection();
        clearForm();
        questionArea.requestFocus();
    }

    private void saveQuestion() {
        Reviewer reviewer = reviewerCombo.getValue();
        if (reviewer == null) {
            UiHelper.showError("No reviewer", "Create or choose a reviewer first.");
            return;
        }
        String questionText = questionArea.getText().trim();
        String answerText = answerArea.getText().trim();
        if (questionText.isEmpty()) {
            UiHelper.showError("Question is empty", "Please type the question before saving.");
            return;
        }

        QuestionDatabase db = app.getDatabase();
        if (editingQuestion == null) {
            db.addQuestion(reviewer.getId(), questionText, answerText);
            loadQuestions();           // the form is cleared, ready for the next new question
        } else {
            editingQuestion.setQuestionText(questionText);
            editingQuestion.setCorrectAnswer(answerText);
            db.updateQuestion(editingQuestion);
            int savedId = editingQuestion.getId();
            loadQuestions();
            for (StudyQuestion question : questionList.getItems()) {
                if (question.getId() == savedId) {
                    questionList.getSelectionModel().select(question);
                }
            }
        }
        if (answerText.isEmpty()) {
            UiHelper.showInfo("Saved", "The question was saved, but it still has no correct answer.\nStudy Mode will not start until you enter one.");
        }
    }

    private void previewQuestion() {
        String questionText = questionArea.getText().trim();
        if (questionText.isEmpty()) {
            UiHelper.showError("Nothing to preview", "Type or select a question first.");
            return;
        }
        UiHelper.showPreviewWindow(app.getStage(), questionText, answerArea.getText().trim());
    }

    private void deleteQuestion() {
        if (editingQuestion == null) {
            UiHelper.showError("Nothing selected", "Select a question in the list first.");
            return;
        }
        if (UiHelper.confirm("Delete question", "Delete this question?\n\n" + editingQuestion.getQuestionText())) {
            app.getDatabase().deleteQuestion(editingQuestion.getId());
            loadQuestions();
        }
    }

    private void importPdf() {
        Reviewer imported = PdfImportDialog.show(app, reviewerCombo.getValue());
        if (imported != null) {
            refresh(imported);
        }
    }
}
