package com.studymate.controller;

import com.studymate.StudyMateApp;
import com.studymate.model.QuizType;
import com.studymate.model.Reviewer;
import com.studymate.model.StudyQuestion;
import com.studymate.service.QuestionDatabase;
import com.studymate.service.StudyService;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Study Mode. It has three screens:
 * 1. Quiz Setup (reviewer, quiz type, number of questions, shuffle)
 * 2. The question screen (looks different for each quiz type)
 * 3. The results screen
 */
public class StudyController {
    private static final int[] COUNT_PRESETS = {5, 10, 15, 20, 30, 50};

    private final StudyMateApp app;
    private final ScrollPane view = UiHelper.scrollable(new VBox());

    private StudyService service = null;      // the running session (null when no session is running)
    private boolean resultsShowing = false;   // true while the results screen is displayed
    private Reviewer activeReviewer = null;   // settings of the running / last session (used for Retry)
    private QuizType activeMode = QuizType.FLASHCARD;
    private boolean activeShuffle = true;
    private int activeLimit = 0;
    private int lastReviewerId = -1;          // remembered choices of the setup screen
    private QuizType lastMode = QuizType.MULTIPLE_CHOICE;

    // widgets of the question screen
    private Label progressLabel;
    private ProgressBar progressBar;
    private Label questionLabel;
    private VBox answerBox;
    private VBox feedbackBox;
    private HBox actionRow;
    private Button previousButton;
    private Button nextButton;
    private Button finishButton;

    public StudyController(StudyMateApp app) {
        this.app = app;
    }

    public Node getView() { return view; }

    /**
     * Called when the Study Mode screen is opened.
     * reviewer != null: show the setup screen with that reviewer selected.
     * reviewer == null: keep a running session / results; otherwise show the setup screen.
     */
    public void refresh(Reviewer reviewer) {
        if (reviewer != null) {
            if (service != null && service.getAnsweredCount() > 0
                    && !UiHelper.confirm("Leave current session?",
                    "You are in the middle of a study session. Leave it and set up a new one?\n"
                            + "Unfinished results are not saved.")) {
                return;
            }
            showSetup(reviewer.getId());
        } else if (service == null && !resultsShowing) {
            showSetup(lastReviewerId);
        }
    }

    private void setScreen(VBox content) {
        UiHelper.padScreen(content);
        view.setContent(content);
        view.setVvalue(0);
    }

    // ================================================================== 1. quiz setup

    private void showSetup(int preselectId) {
        service = null;
        resultsShowing = false;
        QuestionDatabase db = app.getDatabase();

        VBox header = UiHelper.pageHeader("Study Mode", "Choose what to study and how, then start.");
        List<Reviewer> reviewers = db.getAllReviewers();
        if (reviewers.isEmpty()) {
            Button go = UiHelper.button("Go to My Reviewers", "primary-button");
            go.setOnAction(e -> app.showReviewers());
            setScreen(new VBox(20, header, UiHelper.emptyState("You have no reviewers yet",
                    "Create a reviewer or import a PDF first, then come back to study.", go)));
            return;
        }

        // ---- 1. reviewer
        ComboBox<Reviewer> reviewerCombo = new ComboBox<>(FXCollections.observableArrayList(reviewers));
        reviewerCombo.setPrefWidth(360);
        for (Reviewer reviewer : reviewers) {
            if (reviewer.getId() == preselectId) {
                reviewerCombo.setValue(reviewer);
            }
        }
        if (reviewerCombo.getValue() == null) {
            reviewerCombo.setValue(reviewers.get(0));
        }

        // ---- 2. quiz type: three selectable cards
        ToggleGroup modeGroup = new ToggleGroup();
        FlowPane modeRow = new FlowPane(12, 12);
        for (QuizType type : QuizType.values()) {
            ToggleButton card = new ToggleButton(type.getLabel() + "\n" + type.getDescription());
            card.setUserData(type);
            card.setToggleGroup(modeGroup);
            card.getStyleClass().add("mode-card");
            card.setPrefWidth(250);
            card.setMinHeight(70);
            card.setWrapText(true);
            card.setSelected(type == lastMode);
            modeRow.getChildren().add(card);
        }
        // one card must always stay selected
        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });

        // ---- 3. number of questions and shuffle
        ComboBox<Integer> countCombo = new ComboBox<>();
        countCombo.setPrefWidth(200);
        CheckBox shuffleBox = new CheckBox("Shuffle the questions");
        shuffleBox.setSelected(activeShuffle);

        Label summaryLabel = UiHelper.label("", "subtitle-label");
        Label infoLabel = UiHelper.label("", "feedback-info");
        infoLabel.setMaxWidth(Double.MAX_VALUE);
        Button startButton = UiHelper.button("Start Studying", "primary-button");
        Button editorButton = UiHelper.button("Open Question Editor", "secondary-button");

        Runnable updateInfo = () -> {
            Reviewer reviewer = reviewerCombo.getValue();
            if (reviewer == null || modeGroup.getSelectedToggle() == null) {
                return;
            }
            QuizType mode = (QuizType) modeGroup.getSelectedToggle().getUserData();
            lastReviewerId = reviewer.getId();
            lastMode = mode;
            List<StudyQuestion> all = db.getQuestions(reviewer.getId());
            summaryLabel.setText("This reviewer: " + countType(all, QuizType.MULTIPLE_CHOICE) + " multiple choice, "
                    + countType(all, QuizType.FILL_BLANK) + " fill in the blank, "
                    + countType(all, QuizType.FLASHCARD) + " flashcard question(s).");

            List<StudyQuestion> eligible = StudyService.filterCompatible(all, mode);
            int missing = StudyService.countMissingAnswers(eligible);
            String message;
            String style;
            boolean canStart = false;
            if (all.isEmpty()) {
                message = "This reviewer has no questions yet. Add some in the Question Editor.";
                style = "feedback-warning";
            } else if (eligible.isEmpty()) {
                message = "This reviewer has no " + mode.getLabel() + " questions. In the Question Editor, set a "
                        + "question's type to \"" + mode.getLabel() + "\""
                        + (mode == QuizType.MULTIPLE_CHOICE ? " and enter its four options." : " and write its blank.")
                        + "\nTip: Flashcards mode can use every question.";
                style = "feedback-warning";
            } else if (missing > 0) {
                message = missing + " of " + eligible.size() + " " + mode.getLabel() + " question(s) are incomplete "
                        + "(no correct answer" + (mode == QuizType.FLASHCARD ? ", or incomplete multiple choice" : "")
                        + "). Finish them in the Question Editor before studying.";
                style = "feedback-warning";
            } else {
                message = eligible.size() + " " + mode.getLabel() + " question(s) ready. Press Start Studying.";
                style = "feedback-correct";
                canStart = true;
            }
            infoLabel.setText(message);
            infoLabel.getStyleClass().setAll("label", style);
            startButton.setDisable(!canStart);
            countCombo.setDisable(!canStart);
            fillCountChoices(countCombo, canStart ? eligible.size() : 0);
        };
        reviewerCombo.valueProperty().addListener((obs, oldValue, newValue) -> updateInfo.run());
        modeGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> updateInfo.run());
        updateInfo.run();

        startButton.setOnAction(e -> {
            QuizType mode = (QuizType) modeGroup.getSelectedToggle().getUserData();
            int limit = countCombo.getValue() == null ? 0 : countCombo.getValue();
            startSession(reviewerCombo.getValue(), mode, shuffleBox.isSelected(), limit);
        });
        editorButton.setOnAction(e -> app.showEditor(reviewerCombo.getValue()));

        HBox optionsRow = new HBox(14, UiHelper.label("Questions:", "subtitle-label"), countCombo, shuffleBox);
        optionsRow.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(14,
                UiHelper.label("1. Reviewer", "section-title"), reviewerCombo,
                UiHelper.label("2. Quiz type", "section-title"), modeRow,
                UiHelper.label("3. Options", "section-title"), optionsRow,
                summaryLabel, infoLabel,
                new HBox(12, startButton, editorButton));
        card.getStyleClass().add("card");

        setScreen(new VBox(20, header, card));
    }

    private static int countType(List<StudyQuestion> all, QuizType type) {
        int count = 0;
        for (StudyQuestion question : all) {
            if (question.getType() == type) count++;
        }
        return count;
    }

    /** Fills the "how many questions" box: a few presets below the total, and "All". */
    private void fillCountChoices(ComboBox<Integer> combo, int available) {
        List<Integer> choices = new ArrayList<>();
        for (int preset : COUNT_PRESETS) {
            if (preset < available) {
                choices.add(preset);
            }
        }
        if (available > 0) {
            choices.add(available);
        }
        combo.setConverter(new StringConverter<Integer>() {
            @Override
            public String toString(Integer value) {
                if (value == null) return "";
                return value == available ? "All (" + value + ")" : value + " questions";
            }

            @Override
            public Integer fromString(String text) { return null; }
        });
        combo.setItems(FXCollections.observableArrayList(choices));
        if (available > 0) {
            combo.setValue(available);
        }
    }

    /** Checks the questions again and starts the session only if everything is complete. */
    private void startSession(Reviewer reviewer, QuizType mode, boolean shuffle, int limit) {
        if (reviewer == null) {
            UiHelper.showError("No reviewer", "Choose a reviewer first.");
            return;
        }
        List<StudyQuestion> all = app.getDatabase().getQuestions(reviewer.getId());
        List<StudyQuestion> eligible = StudyService.filterCompatible(all, mode);
        if (eligible.isEmpty()) {
            UiHelper.showError("No " + mode.getLabel() + " questions",
                    "\"" + reviewer.getTitle() + "\" has no " + mode.getLabel() + " questions.\n"
                            + "Add or change questions in the Question Editor.");
            return;
        }
        int missing = StudyService.countMissingAnswers(eligible);
        if (missing > 0) {
            UiHelper.showError("Incomplete questions",
                    missing + " question(s) in \"" + reviewer.getTitle() + "\" are incomplete.\n\n"
                            + "Open the Question Editor and finish them (questions marked with a warning sign).");
            return;
        }
        service = new StudyService(eligible, mode, shuffle, limit);
        resultsShowing = false;
        activeReviewer = reviewer;
        activeMode = mode;
        activeShuffle = shuffle;
        activeLimit = limit;
        lastReviewerId = reviewer.getId();
        lastMode = mode;
        showQuestionScreen();
    }

    // ================================================================== 2. question screen

    private void showQuestionScreen() {
        progressLabel = UiHelper.label("", "subtitle-label");
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        questionLabel = UiHelper.label("", "question-text");
        answerBox = new VBox(10);
        feedbackBox = new VBox(10);
        actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        previousButton = UiHelper.button("< Previous", "secondary-button");
        nextButton = UiHelper.button("Next >", "secondary-button");
        finishButton = UiHelper.button("Finish Session", "secondary-button");
        previousButton.setOnAction(e -> {
            service.previous();
            render();
        });
        nextButton.setOnAction(e -> {
            service.next();
            render();
        });
        finishButton.setOnAction(e -> finishEarly());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox navRow = new HBox(10, previousButton, nextButton, spacer, finishButton);
        navRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(16, questionLabel, answerBox, feedbackBox, actionRow);
        card.getStyleClass().add("card");

        HBox titleRow = new HBox(12, UiHelper.label("Study Mode", "page-title"), UiHelper.badge(service.getMode()));
        titleRow.setAlignment(Pos.CENTER_LEFT);
        setScreen(new VBox(16, titleRow, progressLabel, progressBar, card, navRow));
        render();
    }

    /** Redraws the question screen from the session state. */
    private void render() {
        StudyQuestion question = service.getCurrentQuestion();
        int answered = service.getAnsweredCount();
        progressLabel.setText("Question " + service.getCurrentNumber() + " of " + service.getTotal()
                + "   |   Scored so far: " + answered);
        progressBar.setProgress((double) answered / service.getTotal());
        questionLabel.setText(question.getQuestionText());

        answerBox.getChildren().clear();
        feedbackBox.getChildren().clear();
        actionRow.getChildren().clear();

        switch (service.getMode()) {
            case MULTIPLE_CHOICE -> renderMultipleChoice(question);
            case FILL_BLANK -> renderFillBlank(question);
            default -> renderFlashcard(question);
        }

        previousButton.setDisable(!service.hasPrevious());
        nextButton.setDisable(!service.hasNext());
        finishButton.setText(service.isAllAnswered() ? "See Results" : "Finish Session");
        finishButton.getStyleClass().setAll("button", service.isAllAnswered() ? "primary-button" : "secondary-button");
    }

    private void addFeedback(String text, String styleClass) {
        Label label = UiHelper.label(text, styleClass);
        label.setMaxWidth(Double.MAX_VALUE);
        feedbackBox.getChildren().add(label);
    }

    // ---------------------------------------------------------------- multiple choice

    private void renderMultipleChoice(StudyQuestion question) {
        boolean done = service.isRevealed();
        Button submitButton = UiHelper.button("Submit", "primary-button");
        ToggleGroup group = new ToggleGroup();
        for (int i = 0; i < StudyQuestion.OPTION_COUNT; i++) {
            final int index = i;
            ToggleButton option = new ToggleButton((char) ('A' + i) + ".   " + question.getOption(i));
            option.getStyleClass().add("option-button");
            option.setToggleGroup(group);
            option.setMaxWidth(Double.MAX_VALUE);
            option.setWrapText(true);
            option.setSelected(service.getSelectedOption() == i);
            if (done) {
                option.setMouseTransparent(true);
                option.setFocusTraversable(false);
                if (i == question.getCorrectOption()) {
                    option.getStyleClass().add("option-correct");
                } else if (i == service.getSelectedOption()) {
                    option.getStyleClass().add("option-wrong");
                }
            }
            option.setOnAction(e -> {
                option.setSelected(true);           // clicking the chosen option again must not un-select it
                service.selectOption(index);
                submitButton.setDisable(false);
            });
            answerBox.getChildren().add(option);
        }
        submitButton.setDisable(done || service.getSelectedOption() < 0);
        submitButton.setOnAction(e -> {
            service.submitMultipleChoice();
            render();
        });
        actionRow.getChildren().add(submitButton);

        if (done) {
            char letter = (char) ('A' + question.getCorrectOption());
            if (Boolean.TRUE.equals(service.getCurrentResult())) {
                addFeedback("Correct!", "feedback-correct");
            } else {
                addFeedback("Incorrect. The correct answer is " + letter + ". " + question.getCorrectAnswer(), "feedback-wrong");
            }
        }
    }

    // ---------------------------------------------------------------- fill in the blank

    private void renderFillBlank(StudyQuestion question) {
        boolean done = service.isRevealed();
        TextField field = new TextField(service.getTypedAnswer());
        field.setPromptText("Type the missing word or phrase...");
        field.setEditable(!done);
        field.textProperty().addListener((obs, oldValue, newValue) -> service.setTypedAnswer(newValue));
        answerBox.getChildren().add(field);

        Button submitButton = UiHelper.button("Submit Answer", "primary-button");
        Button giveUpButton = UiHelper.button("I Don't Know", "secondary-button");
        submitButton.setDisable(done);
        giveUpButton.setDisable(done);
        Runnable submit = () -> {
            if (service.getTypedAnswer().isBlank()) {
                feedbackBox.getChildren().clear();
                addFeedback("Type an answer first, or press \"I Don't Know\" to see the answer.", "feedback-warning");
                return;
            }
            service.submitFillBlank();
            render();
        };
        submitButton.setOnAction(e -> submit.run());
        field.setOnAction(e -> {
            if (!service.isRevealed()) submit.run();
        });
        giveUpButton.setOnAction(e -> {
            service.submitFillBlank();    // an empty answer is scored as incorrect
            render();
        });
        actionRow.getChildren().addAll(submitButton, giveUpButton);

        if (done) {
            if (Boolean.TRUE.equals(service.getCurrentResult())) {
                addFeedback(service.isCurrentOverridden()
                        ? "Counted as correct by your own judgement. Correct answer: " + question.getCorrectAnswer()
                        : "Correct!", "feedback-correct");
            } else {
                String text = "Not a match. Correct answer: " + question.getCorrectAnswer();
                if (!question.getAcceptedAnswerList().isEmpty()) {
                    text += "\nAlso accepted: " + String.join(", ", question.getAcceptedAnswerList());
                }
                addFeedback(text, "feedback-wrong");
                addFeedback("Was your wording different but right? You can count it as correct.", "feedback-info");
                Button countButton = UiHelper.button("Count it as correct", "success-button");
                countButton.setOnAction(e -> {
                    service.countAsCorrect();
                    render();
                });
                actionRow.getChildren().add(countButton);
            }
        }
        if (!done) {
            field.requestFocus();
        }
    }

    // ---------------------------------------------------------------- flashcards

    private void renderFlashcard(StudyQuestion question) {
        boolean revealed = service.isRevealed();
        Boolean result = service.getCurrentResult();

        TextArea typedArea = new TextArea(service.getTypedAnswer());
        typedArea.setPromptText("Type your answer here (optional)...");
        typedArea.setWrapText(true);
        typedArea.setPrefRowCount(3);
        typedArea.setEditable(!revealed);
        typedArea.textProperty().addListener((obs, oldValue, newValue) -> service.setTypedAnswer(newValue));
        answerBox.getChildren().add(typedArea);

        Button revealButton = UiHelper.button("Reveal Answer", "accent-button");
        revealButton.setDisable(revealed);
        revealButton.setOnAction(e -> {
            service.reveal();
            render();
        });
        actionRow.getChildren().add(revealButton);

        if (revealed) {
            String typed = service.getTypedAnswer().trim();
            String correct = question.getCorrectAnswer().trim();
            VBox compare = new VBox(10,
                    UiHelper.label("Your answer:\n" + (typed.isEmpty() ? "(nothing typed)" : typed), "subtitle-label"),
                    UiHelper.label("Correct answer:\n" + correct, "answer-text"),
                    UiHelper.label(!typed.isEmpty() && typed.equalsIgnoreCase(correct)
                            ? "Your answer matches exactly."
                            : "The wording may differ - judge for yourself if you were right.", "warning-label"));
            compare.getStyleClass().add("answer-compare");
            answerBox.getChildren().add(compare);

            if (result == null) {
                Button correctButton = UiHelper.button("I Was Correct", "success-button");
                Button incorrectButton = UiHelper.button("I Was Incorrect", "danger-button");
                correctButton.setOnAction(e -> markFlashcard(true));
                incorrectButton.setOnAction(e -> markFlashcard(false));
                actionRow.getChildren().addAll(correctButton, incorrectButton);
            } else {
                addFeedback(result ? "Already scored: you were correct." : "Already scored: you were incorrect.",
                        result ? "feedback-correct" : "feedback-wrong");
            }
        } else {
            addFeedback("Reveal the answer, then grade yourself honestly.", "feedback-info");
        }
    }

    private void markFlashcard(boolean wasCorrect) {
        if (!service.mark(wasCorrect)) {
            return; // not revealed yet, or already scored
        }
        if (service.isAllAnswered()) {
            finishSession();
        } else {
            service.goToNextUnmarked();
            render();
        }
    }

    // ================================================================== 3. finishing and results

    private void finishEarly() {
        if (service.getAnsweredCount() == 0) {
            if (UiHelper.confirm("Leave session", "You have not answered any question. Leave without a score?")) {
                showSetup(activeReviewer.getId());
            }
            return;
        }
        if (!service.isAllAnswered()) {
            int left = service.getTotal() - service.getAnsweredCount();
            if (!UiHelper.confirm("Finish now?", left + " question(s) are not answered yet.\n"
                    + "The score will only count the " + service.getAnsweredCount() + " answered question(s).")) {
                return;
            }
        }
        finishSession();
    }

    private void finishSession() {
        int answered = service.getAnsweredCount();
        int correct = service.getCorrectCount();
        int incorrect = service.getIncorrectCount();
        double percentage = service.getPercentage();
        int total = service.getTotal();
        List<StudyQuestion> wrong = service.getIncorrectQuestions();

        app.getDatabase().saveSession(activeReviewer.getId(), activeMode, answered, correct, incorrect, percentage);

        HBox stats = new HBox(16,
                UiHelper.statCard("Final score", UiHelper.label(String.format("%.1f%%", percentage), "stat-value")),
                UiHelper.statCard("Questions answered", UiHelper.label(answered + " of " + total, "stat-value")),
                UiHelper.statCard("Correct", UiHelper.label(String.valueOf(correct), "stat-value")),
                UiHelper.statCard("Incorrect", UiHelper.label(String.valueOf(incorrect), "stat-value")));

        VBox wrongCard = new VBox(12, UiHelper.label("Questions you got wrong", "section-title"));
        wrongCard.getStyleClass().add("card");
        if (wrong.isEmpty()) {
            wrongCard.getChildren().add(UiHelper.label("None - great job!", "success-label"));
        }
        for (StudyQuestion question : wrong) {
            VBox item = new VBox(4, UiHelper.label(question.getQuestionText(), "question-text"),
                    UiHelper.label("Correct answer: " + question.getCorrectAnswer(), "answer-text"));
            item.getStyleClass().add("row-card");
            wrongCard.getChildren().add(item);
        }

        Reviewer reviewer = activeReviewer;
        Button retryButton = UiHelper.button("Retry", "primary-button");
        Button setupButton = UiHelper.button("Study Setup", "secondary-button");
        Button dashboardButton = UiHelper.button("Return to Dashboard", "secondary-button");
        retryButton.setOnAction(e -> startSession(reviewer, activeMode, activeShuffle, activeLimit));
        setupButton.setOnAction(e -> showSetup(reviewer.getId()));
        dashboardButton.setOnAction(e -> {
            showSetup(reviewer.getId());   // so Study Mode opens on the setup screen next time
            app.showDashboard();
        });

        HBox titleRow = new HBox(12, UiHelper.label("Session finished", "page-title"), UiHelper.badge(activeMode));
        titleRow.setAlignment(Pos.CENTER_LEFT);
        setScreen(new VBox(18, titleRow, UiHelper.label(reviewer.getTitle(), "subtitle-label"), stats, wrongCard,
                new FlowPane(12, 12, retryButton, setupButton, dashboardButton)));

        service = null;          // the session is over
        resultsShowing = true;
    }
}
