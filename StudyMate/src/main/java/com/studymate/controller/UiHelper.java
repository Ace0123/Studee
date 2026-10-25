package com.studymate.controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.Optional;

/** Small shared helpers for dialogs and common widgets. */
public class UiHelper {
    private UiHelper() { }

    public static String stylesheetUrl() {
        return Objects.requireNonNull(UiHelper.class.getResource("/studymate.css"),
                "studymate.css not found in src/main/resources").toExternalForm();
    }

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

    public static Button button(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        return button;
    }

    public static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        return label;
    }

    /** A rounded statistic card: small title on top, big number below. */
    public static VBox statCard(String title, Label valueLabel) {
        VBox card = new VBox(6, label(title, "stat-title"), valueLabel);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    /** A small window that shows a question and lets you reveal its answer. */
    public static void showPreviewWindow(Stage owner, String question, String answer) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Question preview");

        Label questionLabel = label(question, "question-text");
        Label answerLabel = label(answer.isBlank() ? "(No correct answer entered yet)" : answer, "answer-text");
        answerLabel.setVisible(false);
        answerLabel.setManaged(false);

        Button revealButton = button("Reveal Answer", "accent-button");
        revealButton.setOnAction(e -> {
            answerLabel.setVisible(true);
            answerLabel.setManaged(true);
            revealButton.setDisable(true);
        });
        Button closeButton = button("Close", "secondary-button");
        closeButton.setOnAction(e -> stage.close());

        VBox box = new VBox(16, questionLabel, revealButton, answerLabel, closeButton);
        box.setPadding(new Insets(24));
        box.getStyleClass().add("main-area");
        Scene scene = new Scene(box, 560, 320);
        scene.getStylesheets().add(stylesheetUrl());
        stage.setScene(scene);
        stage.showAndWait();
    }

    /** Runs code later on the JavaFX thread (used for error pop-ups from the global handler). */
    public static void runLater(Runnable action) {
        Platform.runLater(action);
    }
}
