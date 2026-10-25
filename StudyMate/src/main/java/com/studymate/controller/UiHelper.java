package com.studymate.controller;

import com.studymate.model.QuizType;
import com.studymate.model.StudyQuestion;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.Optional;

/** Small shared helpers for dialogs and common widgets, so every screen looks the same. */
public class UiHelper {
    private UiHelper() { }

    public static String stylesheetUrl() {
        return Objects.requireNonNull(UiHelper.class.getResource("/studymate.css"),
                "studymate.css not found in src/main/resources").toExternalForm();
    }

    // ------------------------------------------------------------ dialogs

    public static void styleDialog(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(stylesheetUrl());
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
    }

    public static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        styleDialog(alert);
        alert.showAndWait();
    }

    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        styleDialog(alert);
        alert.showAndWait();
    }

    /** Shows an OK/Cancel question. Returns true if the user clicked OK. */
    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        styleDialog(alert);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }

    public static Optional<String> askText(String title, String header, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        styleDialog(dialog);
        return dialog.showAndWait();
    }

    // ------------------------------------------------------------ widgets

    public static Button button(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        return button;
    }

    public static Button smallButton(String text, String styleClass) {
        Button button = button(text, styleClass);
        button.getStyleClass().add("small-button");
        return button;
    }

    public static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        return label;
    }

    /** Title + one line of explanation at the top of every screen. */
    public static VBox pageHeader(String title, String subtitle) {
        return new VBox(4, label(title, "page-title"), label(subtitle, "subtitle-label"));
    }

    /** A rounded statistic card: small title on top, big number below. */
    public static VBox statCard(String title, Label valueLabel) {
        VBox card = new VBox(6, label(title, "stat-title"), valueLabel);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinWidth(0);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    /** Friendly box shown when a screen has no data yet. Add buttons with the extra nodes. */
    public static VBox emptyState(String title, String message, Node... extra) {
        VBox box = new VBox(10, label(title, "section-title"), label(message, "subtitle-label"));
        box.getChildren().addAll(extra);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** Wraps a screen in a ScrollPane that follows the window width (and scrolls when the window is small). */
    public static ScrollPane scrollable(Node content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scrollPane;
    }

    /** Standard padding of every screen. */
    public static void padScreen(VBox box) {
        box.setPadding(new Insets(30));
    }

    /** Text such as "Multiple Choice" in a small rounded badge. */
    public static Label badge(QuizType type) {
        Label label = new Label(type.getLabel());
        label.getStyleClass().add("badge");
        return label;
    }

    // ------------------------------------------------------------ preview window

    /** Shows how a question will look when studying, with a button to reveal the correct answer. */
    public static void showPreviewWindow(Stage owner, StudyQuestion question) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Question preview");

        VBox box = new VBox(14);
        box.setPadding(new Insets(24));
        box.getStyleClass().add("main-area");
        box.getChildren().addAll(badge(question.getType()), label(question.getQuestionText(), "question-text"));

        Label[] optionLabels = new Label[StudyQuestion.OPTION_COUNT];
        if (question.getType() == QuizType.MULTIPLE_CHOICE) {
            for (int i = 0; i < StudyQuestion.OPTION_COUNT; i++) {
                optionLabels[i] = label((char) ('A' + i) + ".  " + question.getOption(i), "feedback-info");
                optionLabels[i].setMaxWidth(Double.MAX_VALUE);
                box.getChildren().add(optionLabels[i]);
            }
        }

        String answerText = question.hasAnswer() ? "Correct answer: " + question.getCorrectAnswer()
                : "(No correct answer entered yet)";
        if (question.getType() == QuizType.FILL_BLANK && !question.getAcceptedAnswerList().isEmpty()) {
            answerText += "\nAlso accepted: " + String.join(", ", question.getAcceptedAnswerList());
        }
        Label answerLabel = label(answerText, "answer-text");
        answerLabel.setVisible(false);
        answerLabel.setManaged(false);

        Button revealButton = button("Reveal Answer", "accent-button");
        revealButton.setOnAction(e -> {
            answerLabel.setVisible(true);
            answerLabel.setManaged(true);
            revealButton.setDisable(true);
            if (question.getType() == QuizType.MULTIPLE_CHOICE && question.getCorrectOption() >= 0) {
                optionLabels[question.getCorrectOption()].getStyleClass().setAll("label", "feedback-correct");
            }
        });
        Button closeButton = button("Close", "secondary-button");
        closeButton.setOnAction(e -> stage.close());
        box.getChildren().addAll(answerLabel, new HBox(10, revealButton, closeButton));

        Scene scene = new Scene(UiHelper.scrollable(box), 620, 460);
        scene.getStylesheets().add(stylesheetUrl());
        stage.setScene(scene);
        stage.showAndWait();
    }

    /** Runs code later on the JavaFX thread (used for error pop-ups from the global handler). */
    public static void runLater(Runnable action) {
        Platform.runLater(action);
    }
}
