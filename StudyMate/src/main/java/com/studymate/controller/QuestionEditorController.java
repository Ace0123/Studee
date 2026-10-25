package com.studymate.controller;

import com.studymate.StudyMateApp;
import com.studymate.model.QuizType;
import com.studymate.model.Reviewer;
import com.studymate.model.StudyQuestion;
import com.studymate.service.QuestionDatabase;
import com.studymate.service.StudyService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;

/** Question Editor: search, add, edit, preview and delete questions of every type. */
public class QuestionEditorController {
    private static final String FILTER_ALL = "All questions";
    private static final String FILTER_MISSING = "Missing answer";

    private final StudyMateApp app;
    private final ScrollPane view;
    private final ComboBox<Reviewer> reviewerCombo = new ComboBox<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> filterCombo = new ComboBox<>();
    private final ObservableList<StudyQuestion> allQuestions = FXCollections.observableArrayList();
    private final FilteredList<StudyQuestion> filteredQuestions = new FilteredList<>(allQuestions, q -> true);
    private final ListView<StudyQuestion> questionList = new ListView<>(filteredQuestions);
    private final Label summaryLabel = UiHelper.label("", "subtitle-label");

    // form
    private final VBox formCard = new VBox(12);
    private final Label formTitle = UiHelper.label("New question", "section-title");
    private final ComboBox<QuizType> typeCombo = new ComboBox<>();
    private final TextArea questionArea = new TextArea();
    private final Label questionHint = UiHelper.label("", "subtitle-label");
    private final TextArea answerArea = new TextArea();
    private final TextArea acceptedArea = new TextArea();
    private final VBox answerSection = new VBox(6);
    private final VBox acceptedSection = new VBox(6);
    private final VBox optionsSection = new VBox(8);
    private final TextField[] optionFields = new TextField[StudyQuestion.OPTION_COUNT];
    private final RadioButton[] optionRadios = new RadioButton[StudyQuestion.OPTION_COUNT];
    private final Label[] optionMarks = new Label[StudyQuestion.OPTION_COUNT];
    private final HBox[] optionRows = new HBox[StudyQuestion.OPTION_COUNT];
    private final ToggleGroup correctGroup = new ToggleGroup();

    private StudyQuestion editingQuestion = null; // null = the form is for a NEW question
    private boolean loading = false;              // true while we fill the controls ourselves

    public QuestionEditorController(StudyMateApp app) {
        this.app = app;

        // ---- top row: reviewer + shortcuts
        reviewerCombo.setPrefWidth(280);
        reviewerCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!loading) {
                loadQuestions();
            }
        });
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
        // buttons keep their full text; the reviewer box shrinks when the window is narrow
        Label reviewerLabel = UiHelper.label("Reviewer", "field-label");
        reviewerLabel.setWrapText(false);
        reviewerLabel.setMinWidth(Region.USE_PREF_SIZE);
        reviewerCombo.setMinWidth(150);
        importButton.setMinWidth(Region.USE_PREF_SIZE);
        studyButton.setMinWidth(Region.USE_PREF_SIZE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(12, reviewerLabel, reviewerCombo, spacer, importButton, studyButton);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // ---- left: search + list
        searchField.setPromptText("Search questions...");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        filterCombo.setItems(FXCollections.observableArrayList(FILTER_ALL,
                QuizType.MULTIPLE_CHOICE.getLabel(), QuizType.FILL_BLANK.getLabel(), QuizType.FLASHCARD.getLabel(),
                FILTER_MISSING));
        filterCombo.setValue(FILTER_ALL);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilter());
        filterCombo.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());
        HBox searchRow = new HBox(10, searchField, filterCombo);

        questionList.setPlaceholder(UiHelper.label("No questions to show.\nAdd one on the right or import a PDF.", "subtitle-label"));
        questionList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(StudyQuestion item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String text = item.getQuestionText().replace("\n", " ");
                    setText((item.hasAnswer() ? "" : "\u26A0 ") + "[" + shortType(item.getType()) + "] " + text);
                }
            }
        });
        questionList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> {
            if (selected != null) {
                loadIntoForm(selected);
            }
        });
        VBox.setVgrow(questionList, Priority.ALWAYS);
        VBox leftBox = new VBox(10, UiHelper.label("Questions", "section-title"), searchRow, questionList, summaryLabel);
        leftBox.setPrefWidth(430);
        leftBox.setMinWidth(300);
        HBox.setHgrow(leftBox, Priority.ALWAYS);

        // ---- right: form
        buildForm();
        formCard.setPrefWidth(460);
        formCard.setMinWidth(340);
        formCard.setMinHeight(Region.USE_PREF_SIZE);   // never squeeze the form: the page scrolls instead
        HBox.setHgrow(formCard, Priority.ALWAYS);

        HBox middle = new HBox(20, leftBox, formCard);
        VBox.setVgrow(middle, Priority.ALWAYS);

        VBox content = new VBox(16, UiHelper.pageHeader("Question Editor",
                "Edit questions and TYPE the correct answers. Study Mode needs every answer filled in."),
                topRow, middle);
        UiHelper.padScreen(content);
        questionList.setMinHeight(320);   // below this height the page scrolls; above it the list grows
        view = UiHelper.scrollable(content);
        view.setFitToHeight(true);
    }

    private static String shortType(QuizType type) {
        return type == QuizType.MULTIPLE_CHOICE ? "MC" : type == QuizType.FILL_BLANK ? "Blank" : "Card";
    }

    private void buildForm() {
        typeCombo.setItems(FXCollections.observableArrayList(QuizType.values()));
        typeCombo.setValue(QuizType.FLASHCARD);
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        typeCombo.valueProperty().addListener((obs, oldType, newType) -> onTypeChanged(oldType, newType));

        questionArea.setPromptText("Type the question here...");
        questionArea.setWrapText(true);
        questionArea.setPrefRowCount(4);

        answerArea.setPromptText("Type the CORRECT answer here (you decide, not the program)...");
        answerArea.setWrapText(true);
        answerArea.setPrefRowCount(3);
        answerSection.getChildren().addAll(UiHelper.label("Correct answer", "field-label"), answerArea);

        acceptedArea.setPromptText("One alternative answer per line (optional)");
        acceptedArea.setWrapText(true);
        acceptedArea.setPrefRowCount(2);
        acceptedSection.getChildren().addAll(UiHelper.label("Other accepted answers (optional)", "field-label"), acceptedArea);

        optionsSection.getChildren().add(UiHelper.label(
                "Options - click the circle next to the CORRECT one", "field-label"));
        for (int i = 0; i < StudyQuestion.OPTION_COUNT; i++) {
            final int index = i;
            optionRadios[i] = new RadioButton((char) ('A' + i) + "");
            optionRadios[i].setToggleGroup(correctGroup);
            optionFields[i] = new TextField();
            optionFields[i].setPromptText("Option " + (char) ('A' + i));
            HBox.setHgrow(optionFields[i], Priority.ALWAYS);
            optionMarks[i] = UiHelper.label("\u2713 Correct", "correct-mark");
            optionMarks[i].setVisible(false);
            optionMarks[i].setWrapText(false);
            optionMarks[i].setMinWidth(Region.USE_PREF_SIZE);
            optionRows[i] = new HBox(10, optionRadios[i], optionFields[i], optionMarks[i]);
            optionRows[i].setAlignment(Pos.CENTER_LEFT);
            optionRows[i].getStyleClass().add("option-row");
            optionRadios[i].selectedProperty().addListener((obs, was, now) -> updateCorrectMarks());
            optionsSection.getChildren().add(optionRows[index]);
        }

        Button newButton = UiHelper.button("New", "secondary-button");
        Button saveButton = UiHelper.button("Save", "primary-button");
        Button cancelButton = UiHelper.button("Cancel", "secondary-button");
        Button previewButton = UiHelper.button("Preview", "accent-button");
        Button deleteButton = UiHelper.button("Delete", "danger-button");
        newButton.setOnAction(e -> startNewQuestion());
        saveButton.setOnAction(e -> saveQuestion());
        cancelButton.setOnAction(e -> cancelEditing());
        previewButton.setOnAction(e -> previewQuestion());
        deleteButton.setOnAction(e -> deleteQuestion());
        FlowPane buttons = new FlowPane(10, 10, saveButton, cancelButton, previewButton, newButton, deleteButton);

        formCard.getChildren().addAll(formTitle,
                UiHelper.label("Question type", "field-label"), typeCombo,
                UiHelper.label("Question", "field-label"), questionArea, questionHint,
                answerSection, acceptedSection, optionsSection, buttons);
        formCard.getStyleClass().add("card");
        showSectionsFor(QuizType.FLASHCARD);
    }

    /** Shows only the controls the chosen type needs. */
    private void showSectionsFor(QuizType type) {
        setVisible(optionsSection, type == QuizType.MULTIPLE_CHOICE);
        setVisible(answerSection, type != QuizType.MULTIPLE_CHOICE);
        setVisible(acceptedSection, type == QuizType.FILL_BLANK);
        questionHint.setText(type == QuizType.FILL_BLANK
                ? "Write the sentence and mark the missing word with _____ (five underscores)."
                : type == QuizType.MULTIPLE_CHOICE ? "Enter four different options below and mark the correct one." : "");
        setVisible(questionHint, !questionHint.getText().isEmpty());
    }

    private void setVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void onTypeChanged(QuizType oldType, QuizType newType) {
        if (newType == null) {
            return;
        }
        showSectionsFor(newType);
        if (loading) {
            return;
        }
        // Be helpful when switching an existing question between types.
        if (newType == QuizType.MULTIPLE_CHOICE && allOptionsBlank() && !answerArea.getText().isBlank()) {
            optionFields[0].setText(answerArea.getText().trim());
            optionRadios[0].setSelected(true);
        } else if (oldType == QuizType.MULTIPLE_CHOICE && newType != QuizType.MULTIPLE_CHOICE
                && answerArea.getText().isBlank() && correctGroup.getSelectedToggle() != null) {
            answerArea.setText(optionFields[selectedOptionIndex()].getText().trim());
        }
    }

    private boolean allOptionsBlank() {
        for (TextField field : optionFields) {
            if (!field.getText().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private int selectedOptionIndex() {
        for (int i = 0; i < optionRadios.length; i++) {
            if (optionRadios[i].isSelected()) {
                return i;
            }
        }
        return -1;
    }

    private void updateCorrectMarks() {
        for (int i = 0; i < optionRadios.length; i++) {
            boolean correct = optionRadios[i].isSelected();
            optionMarks[i].setVisible(correct);
            if (correct) {
                if (!optionRows[i].getStyleClass().contains("option-row-correct")) {
                    optionRows[i].getStyleClass().add("option-row-correct");
                }
            } else {
                optionRows[i].getStyleClass().remove("option-row-correct");
            }
        }
    }

    public Node getView() { return view; }

    // ------------------------------------------------------------ loading data

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
            allQuestions.clear();
            summaryLabel.setText("Create a reviewer first (My Reviewers, or import a PDF).");
            return;
        }
        List<StudyQuestion> questions = app.getDatabase().getQuestions(reviewer.getId());
        allQuestions.setAll(questions);
        int missing = StudyService.countMissingAnswers(questions);
        summaryLabel.setText(questions.size() + " question(s), " + missing + " incomplete (\u26A0).");
    }

    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String filter = filterCombo.getValue();
        filteredQuestions.setPredicate(q -> matchesFilter(q, filter) && matchesSearch(q, query));
    }

    private boolean matchesFilter(StudyQuestion q, String filter) {
        if (filter == null || filter.equals(FILTER_ALL)) {
            return true;
        }
        if (filter.equals(FILTER_MISSING)) {
            return !q.hasAnswer();
        }
        return q.getType().getLabel().equals(filter);
    }

    private boolean matchesSearch(StudyQuestion q, String query) {
        if (query.isEmpty()) {
            return true;
        }
        StringBuilder all = new StringBuilder(q.getQuestionText()).append(' ').append(q.getCorrectAnswer());
        for (int i = 0; i < StudyQuestion.OPTION_COUNT; i++) {
            all.append(' ').append(q.getOption(i));
        }
        return all.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    // ------------------------------------------------------------ form <-> question

    private void loadIntoForm(StudyQuestion question) {
        loading = true;
        editingQuestion = question;
        typeCombo.setValue(question.getType());
        questionArea.setText(question.getQuestionText());
        answerArea.setText(question.getType() == QuizType.MULTIPLE_CHOICE ? "" : question.getCorrectAnswer());
        acceptedArea.setText(question.getAcceptedAnswers());
        for (int i = 0; i < StudyQuestion.OPTION_COUNT; i++) {
            optionFields[i].setText(question.getOption(i));
            optionRadios[i].setSelected(i == question.getCorrectOption());
        }
        if (question.getCorrectOption() < 0) {
            correctGroup.selectToggle(null);
        }
        updateCorrectMarks();
        formTitle.setText("Edit question");
        loading = false;
    }

    private void clearForm() {
        loading = true;
        editingQuestion = null;
        questionArea.clear();
        answerArea.clear();
        acceptedArea.clear();
        for (int i = 0; i < StudyQuestion.OPTION_COUNT; i++) {
            optionFields[i].clear();
        }
        correctGroup.selectToggle(null);
        updateCorrectMarks();
        formTitle.setText("New question");
        loading = false;
    }

    /** Builds a question from the form. Throws IllegalArgumentException with a friendly message if it is invalid. */
    private StudyQuestion readForm(int reviewerId) {
        QuizType type = typeCombo.getValue();
        String text = questionArea.getText().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Please type the question first.");
        }
        int id = editingQuestion == null ? 0 : editingQuestion.getId();
        if (type == QuizType.MULTIPLE_CHOICE) {
            String[] options = new String[StudyQuestion.OPTION_COUNT];
            for (int i = 0; i < options.length; i++) {
                options[i] = optionFields[i].getText().trim();
                if (options[i].isEmpty()) {
                    throw new IllegalArgumentException("Fill in all four options (option " + (char) ('A' + i) + " is empty).");
                }
            }
            StudyQuestion question = new StudyQuestion(id, reviewerId, type, text, "", "", options, selectedOptionIndex());
            if (selectedOptionIndex() < 0) {
                throw new IllegalArgumentException("Choose the correct option by clicking the circle next to it.");
            }
            if (!question.isValidMultipleChoice()) {
                throw new IllegalArgumentException("The four options must all be different.");
            }
            return question;
        }
        String accepted = type == QuizType.FILL_BLANK ? acceptedArea.getText().trim() : "";
        return new StudyQuestion(id, reviewerId, type, text, answerArea.getText().trim(), accepted, null, -1);
    }

    // ------------------------------------------------------------ actions

    private void startNewQuestion() {
        questionList.getSelectionModel().clearSelection();
        clearForm();
        questionArea.requestFocus();
    }

    /** Cancel: throw away unsaved changes. */
    private void cancelEditing() {
        if (editingQuestion != null) {
            loadIntoForm(editingQuestion);   // back to the saved version
        } else {
            clearForm();
        }
    }

    private void saveQuestion() {
        Reviewer reviewer = reviewerCombo.getValue();
        if (reviewer == null) {
            UiHelper.showError("No reviewer", "Create or choose a reviewer first.");
            return;
        }
        StudyQuestion question;
        try {
            question = readForm(reviewer.getId());
        } catch (IllegalArgumentException e) {
            UiHelper.showError("Cannot save yet", e.getMessage());
            return;
        }

        QuestionDatabase db = app.getDatabase();
        int savedId;
        boolean isNew = editingQuestion == null;
        if (isNew) {
            savedId = db.addQuestion(question).getId();
        } else {
            db.updateQuestion(question);
            savedId = question.getId();
        }
        QuizType keepType = question.getType();
        loadQuestions();
        if (isNew) {
            typeCombo.setValue(keepType);   // ready for the next question of the same type
        } else {
            for (StudyQuestion item : allQuestions) {
                if (item.getId() == savedId) {
                    questionList.getSelectionModel().select(item);
                }
            }
        }
        if (!question.hasAnswer()) {
            UiHelper.showInfo("Saved", "The question was saved, but it still has no correct answer.\n"
                    + "Study Mode will not start until you enter one.");
        }
    }

    private void previewQuestion() {
        Reviewer reviewer = reviewerCombo.getValue();
        if (reviewer == null) {
            return;
        }
        try {
            UiHelper.showPreviewWindow(app.getStage(), readForm(reviewer.getId()));
        } catch (IllegalArgumentException e) {
            UiHelper.showError("Nothing to preview", e.getMessage());
        }
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
