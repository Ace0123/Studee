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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/** Study Mode: choose a reviewer, answer flashcards, see the score. Has three screens: setup, question, results. */
public class StudyController {
    private final StudyMateApp app;
    private final ScrollPane view = new ScrollPane();

    private StudyService service = null;      // the running session (null when no session is running)
    private boolean resultsShowing = false;   // true while the results screen is displayed
    private Reviewer activeReviewer = null;   // reviewer of the running / last session
    private boolean shuffleSetting = false;
    private int lastReviewerId = -1;          // remembers the reviewer picked on the setup screen

    // widgets of the question screen
    private Label progressLabel;
    private ProgressBar progressBar;
    private Label questionLabel;
    private TextArea typedArea;
    private Button revealButton;
    private VBox compareBox;
    private Label yourAnswerLabel;
    private Label correctAnswerLabel;
    private Label matchLabel;
    private Label statusLabel;
    private Button correctButton;
    private Button incorrectButton;
    private Button previousButton;
    private Button nextButton;

    public StudyController(StudyMateApp app) {
        this.app = app;
        view.setFitToWidth(true);
    }

    public Node getView() { return view; }

    /**
     * Called when the Study Mode screen is opened.
     * reviewer != null: show the setup screen with that reviewer selected.
     * reviewer == null: keep a running session / results; otherwise show the setup screen.
     */
    public void refresh(Reviewer reviewer) {
        if (reviewer != null) {
            showSetup(reviewer.getId());
        } else if (service == null && !resultsShowing) {
            showSetup(lastReviewerId);
        }
    }

    // ------------------------------------------------------------------ setup screen

    private void showSetup(int preselectId) {
        service = null;
        resultsShowing = false;
        QuestionDatabase db = app.getDatabase();

        Label title = UiHelper.label("Study Mode", "page-title");
        Label subtitle = UiHelper.label("Pick a reviewer and test yourself with flashcards.", "subtitle-label");

        List<Reviewer> reviewers = db.getAllReviewers();
        if (reviewers.isEmpty()) {
            VBox empty = new VBox(14, title, subtitle,
                    UiHelper.label("You have no reviewers yet. Import a PDF or create a reviewer first.", "warning-label"));
            empty.setPadding(new Insets(30));
            view.setContent(empty);
            return;
        }

        ComboBox<Reviewer> combo = new ComboBox<>(FXCollections.observableArrayList(reviewers));
        combo.setPrefWidth(340);
        for (Reviewer reviewer : reviewers) {
            if (reviewer.getId() == preselectId) {
                combo.setValue(reviewer);
            }
        }
        if (combo.getValue() == null) {
            combo.setValue(reviewers.get(0));
        }

        Label infoLabel = UiHelper.label("", "subtitle-label");
        CheckBox shuffleBox = new CheckBox("Shuffle the questions");
        Button startButton = UiHelper.button("Start Studying", "primary-button");
        Button editorButton = UiHelper.button("Open Question Editor", "secondary-button");

        Runnable updateInfo = () -> {
            Reviewer chosen = combo.getValue();
            if (chosen == null) {
                infoLabel.setText("");
                return;
            }
            lastReviewerId = chosen.getId();
            List<StudyQuestion> questions = db.getQuestions(chosen.getId());
            int missing = StudyService.countMissingAnswers(questions);
            if (questions.isEmpty()) {
                infoLabel.setText("This reviewer has no questions yet. Add some in the Question Editor.");
            } else if (missing > 0) {
                infoLabel.setText(questions.size() + " question(s), but " + missing
                        + " have no correct answer. Fill them in before studying.");
            } else {
                infoLabel.setText(questions.size() + " question(s), all with correct answers. Ready!");
            }
        };
        combo.valueProperty().addListener((obs, oldValue, newValue) -> updateInfo.run());
        updateInfo.run();

        startButton.setOnAction(e -> startSession(combo.getValue(), shuffleBox.isSelected()));
        editorButton.setOnAction(e -> app.showEditor(combo.getValue()));

        VBox card = new VBox(14, UiHelper.label("Choose a reviewer", "section-title"), combo, infoLabel, shuffleBox,
                new HBox(12, startButton, editorButton));
        card.getStyleClass().add("card");

        VBox content = new VBox(18, title, subtitle, card);
        content.setPadding(new Insets(30));
        view.setContent(content);
    }

    /** Checks the questions and starts the session only if every answer is filled in. */
    private void startSession(Reviewer reviewer, boolean shuffle) {
        if (reviewer == null) {
            UiHelper.showError("No reviewer", "Choose a reviewer first.");
            return;
        }
        List<StudyQuestion> questions = app.getDatabase().getQuestions(reviewer.getId());
        if (questions.isEmpty()) {
            UiHelper.showError("No questions", "\"" + reviewer.getTitle() + "\" has no questions yet.\nAdd some in the Question Editor.");
            return;
        }
        int missing = StudyService.countMissingAnswers(questions);
        if (missing > 0) {
            UiHelper.showError("Missing correct answers",
                    missing + " question(s) in \"" + reviewer.getTitle() + "\" have no correct answer.\n\n"
                            + "Open the Question Editor and type the correct answers (questions marked with a warning sign).");
            return;
        }
        service = new StudyService(questions, shuffle);
        resultsShowing = false;
        activeReviewer = reviewer;
        shuffleSetting = shuffle;
        lastReviewerId = reviewer.getId();
        showQuestionScreen();
    }

    // ------------------------------------------------------------------ question screen

    private void showQuestionScreen() {
        progressLabel = UiHelper.label("", "subtitle-label");
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        questionLabel = UiHelper.label("", "question-text");

        typedArea = new TextArea();
        typedArea.setPromptText("Type your answer here...");
        typedArea.setWrapText(true);
        typedArea.setPrefRowCount(4);
        typedArea.textProperty().addListener((obs, oldValue, newValue) -> {
            if (service != null) {
                service.setTypedAnswer(newValue);
            }
        });

        revealButton = UiHelper.button("Reveal Answer", "accent-button");
        revealButton.setOnAction(e -> {
            service.reveal();
            render();
        });

        yourAnswerLabel = UiHelper.label("", "subtitle-label");
        correctAnswerLabel = UiHelper.label("", "answer-text");
        matchLabel = UiHelper.label("", "warning-label");
        compareBox = new VBox(10, yourAnswerLabel, correctAnswerLabel, matchLabel);
        compareBox.getStyleClass().add("answer-compare");

        correctButton = UiHelper.button("I Was Correct", "success-button");
        incorrectButton = UiHelper.button("I Was Incorrect", "danger-button");
        correctButton.setOnAction(e -> mark(true));
        incorrectButton.setOnAction(e -> mark(false));
        statusLabel = UiHelper.label("", "subtitle-label");

        previousButton = UiHelper.button("< Previous", "secondary-button");
        nextButton = UiHelper.button("Next >", "secondary-button");
        Button finishButton = UiHelper.button("Finish Session", "secondary-button");
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
        HBox markRow = new HBox(12, correctButton, incorrectButton, statusLabel);
        markRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(14, questionLabel, typedArea, revealButton, compareBox, markRow);
        card.getStyleClass().add("card");

        VBox content = new VBox(16, UiHelper.label("Study Mode", "page-title"), progressLabel, progressBar, card, navRow);
        content.setPadding(new Insets(30));
        view.setContent(content);
        render();
    }

    /** Updates every widget of the question screen from the session state. */
    private void render() {
        StudyQuestion question = service.getCurrentQuestion();
        boolean revealed = service.isRevealed();
        Boolean result = service.getCurrentResult();
        int answered = service.getAnsweredCount();

        progressLabel.setText("Question " + service.getCurrentNumber() + " of " + service.getTotal()
                + "   |   Marked so far: " + answered);
        progressBar.setProgress((double) answered / service.getTotal());
        questionLabel.setText(question.getQuestionText());

        typedArea.setText(service.getTypedAnswer());
        typedArea.setEditable(!revealed);
        revealButton.setDisable(revealed);

        compareBox.setVisible(revealed);
        compareBox.setManaged(revealed);
        if (revealed) {
            String typed = service.getTypedAnswer().trim();
            String correct = question.getCorrectAnswer().trim();
            yourAnswerLabel.setText("Your answer:\n" + (typed.isEmpty() ? "(nothing typed)" : typed));
            correctAnswerLabel.setText("Correct answer:\n" + correct);
            if (!typed.isEmpty() && typed.equalsIgnoreCase(correct)) {
                matchLabel.setText("Your answer matches exactly.");
            } else {
                matchLabel.setText("The wording is different - judge for yourself if you were right.");
            }
        }

        boolean canMark = revealed && result == null;
        correctButton.setDisable(!canMark);
        incorrectButton.setDisable(!canMark);
        if (result == null) {
            statusLabel.setText(revealed ? "Mark yourself honestly." : "Reveal the answer first.");
        } else {
            statusLabel.setText(result ? "Already marked: I was correct." : "Already marked: I was incorrect.");
        }

        previousButton.setDisable(!service.hasPrevious());
        nextButton.setDisable(!service.hasNext());
    }

    private void mark(boolean wasCorrect) {
        if (!service.mark(wasCorrect)) {
            return; // not revealed yet, or already marked
        }
        if (service.isAllAnswered()) {
            finishSession();
        } else {
            service.goToNextUnmarked();
            render();
        }
    }

    private void finishEarly() {
        if (service.getAnsweredCount() == 0) {
            if (UiHelper.confirm("Leave session", "You have not marked any question. Leave without a score?")) {
                showSetup(activeReviewer.getId());
            }
            return;
        }
        if (!service.isAllAnswered()) {
            int left = service.getTotal() - service.getAnsweredCount();
            if (!UiHelper.confirm("Finish now?", left + " question(s) are not marked yet.\n"
                    + "The score will only count the " + service.getAnsweredCount() + " marked question(s).")) {
                return;
            }
        }
        finishSession();
    }

    // ------------------------------------------------------------------ results screen

    private void finishSession() {
        int answered = service.getAnsweredCount();
        int correct = service.getCorrectCount();
        int incorrect = service.getIncorrectCount();
        double percentage = service.getPercentage();
        int total = service.getTotal();
        List<StudyQuestion> wrong = service.getIncorrectQuestions();

        app.getDatabase().saveSession(activeReviewer.getId(), answered, correct, incorrect, percentage);

        Label percentLabel = UiHelper.label(String.format("%.1f%%", percentage), "stat-value");
        Label answeredLabel = UiHelper.label(answered + " of " + total, "stat-value");
        Label correctLabel = UiHelper.label(String.valueOf(correct), "stat-value");
        Label incorrectLabel = UiHelper.label(String.valueOf(incorrect), "stat-value");
        HBox stats = new HBox(16,
                UiHelper.statCard("Final score", percentLabel),
                UiHelper.statCard("Questions answered", answeredLabel),
                UiHelper.statCard("Correct", correctLabel),
                UiHelper.statCard("Incorrect", incorrectLabel));

        VBox wrongCard = new VBox(10, UiHelper.label("Questions you marked incorrect", "section-title"));
        wrongCard.getStyleClass().add("card");
        if (wrong.isEmpty()) {
            wrongCard.getChildren().add(UiHelper.label("None - great job!", "subtitle-label"));
        }
        for (StudyQuestion question : wrong) {
            wrongCard.getChildren().add(UiHelper.label(question.getQuestionText(), "question-text"));
            wrongCard.getChildren().add(UiHelper.label("Correct answer: " + question.getCorrectAnswer(), "answer-text"));
        }

        Reviewer reviewer = activeReviewer;
        boolean shuffle = shuffleSetting;
        Button restartButton = UiHelper.button("Restart Reviewer", "primary-button");
        Button backButton = UiHelper.button("Back to Study Setup", "secondary-button");
        restartButton.setOnAction(e -> startSession(reviewer, shuffle));
        backButton.setOnAction(e -> showSetup(reviewer.getId()));

        VBox content = new VBox(18, UiHelper.label("Session finished: " + reviewer.getTitle(), "page-title"),
                stats, wrongCard, new HBox(12, restartButton, backButton));
        content.setPadding(new Insets(30));
        view.setContent(content);
        view.setVvalue(0);

        service = null;          // the session is over
        resultsShowing = true;
    }
}
