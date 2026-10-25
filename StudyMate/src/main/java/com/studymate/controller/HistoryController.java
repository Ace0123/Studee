package com.studymate.controller;

import com.studymate.StudyMateApp;
import com.studymate.model.StudySession;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/** "Study History" screen: every finished session with date, reviewer, quiz type and score. */
public class HistoryController {
    private final StudyMateApp app;
    private final ScrollPane view;
    private final Label summaryLabel = UiHelper.label("", "subtitle-label");
    private final VBox rowsBox = new VBox(8);
    private final Button clearButton = UiHelper.button("Clear History", "danger-button");
    private final VBox emptyBox;

    public HistoryController(StudyMateApp app) {
        this.app = app;
        clearButton.setOnAction(e -> clearHistory());

        emptyBox = UiHelper.emptyState("No study sessions yet",
                "Finish a study session and its result will be listed here.");
        Button studyButton = UiHelper.button("Start Studying", "primary-button");
        studyButton.setOnAction(e -> app.showStudy(null));
        emptyBox.getChildren().add(studyButton);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(12, summaryLabel, spacer, clearButton);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(18, UiHelper.pageHeader("Study History",
                "All your finished study sessions, newest first."), topRow, emptyBox, rowsBox);
        UiHelper.padScreen(content);
        view = UiHelper.scrollable(content);
    }

    public Node getView() { return view; }

    public void refresh() {
        List<StudySession> sessions = app.getDatabase().getAllSessions();
        rowsBox.getChildren().clear();
        boolean empty = sessions.isEmpty();
        emptyBox.setVisible(empty);
        emptyBox.setManaged(empty);
        clearButton.setDisable(empty);
        summaryLabel.setText(empty ? "" : sessions.size() + " session(s)  |  average score "
                + String.format("%.1f%%", app.getDatabase().getAverageScore()));
        if (empty) {
            return;
        }
        rowsBox.getChildren().add(buildRow(true, "Date", "Reviewer", "Quiz type", "Correct", "Score", null));
        for (StudySession session : sessions) {
            rowsBox.getChildren().add(buildRow(false, session.getCompletedAt(), session.getReviewerTitle(),
                    session.getQuizType().getLabel(),
                    session.getCorrectCount() + " / " + session.getTotalAnswered(),
                    String.format("%.1f%%", session.getPercentage()), session));
        }
    }

    /** One table-like row. Percent widths make the columns follow the window width. */
    private GridPane buildRow(boolean header, String date, String reviewer, String type, String correct,
                              String score, StudySession session) {
        GridPane row = new GridPane();
        row.setHgap(10);
        double[] widths = {22, 28, 20, 14, 16};
        for (double width : widths) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(width);
            row.getColumnConstraints().add(column);
        }
        String style = header ? "field-label" : "subtitle-label";
        Label scoreLabel = UiHelper.label(score, style);
        if (!header) {
            double pct = session.getPercentage();
            scoreLabel.getStyleClass().setAll("label", pct >= 75 ? "score-good" : pct >= 50 ? "score-ok" : "score-low");
            scoreLabel.setWrapText(true);
        }
        row.add(UiHelper.label(date, style), 0, 0);
        row.add(UiHelper.label(reviewer, style), 1, 0);
        row.add(UiHelper.label(type, style), 2, 0);
        row.add(UiHelper.label(correct, style), 3, 0);
        row.add(scoreLabel, 4, 0);
        row.getStyleClass().add(header ? "history-header" : "history-row");
        return row;
    }

    private void clearHistory() {
        if (UiHelper.confirm("Clear study history",
                "Delete ALL saved study sessions?\n\nYour reviewers and questions are not affected. This cannot be undone.")) {
            app.getDatabase().deleteAllSessions();
            refresh();
        }
    }
}
