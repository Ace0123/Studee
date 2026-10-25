package com.studymate;

import com.studymate.controller.DashboardController;
import com.studymate.controller.QuestionEditorController;
import com.studymate.controller.ReviewerController;
import com.studymate.controller.StudyController;
import com.studymate.controller.UiHelper;
import com.studymate.model.Reviewer;
import com.studymate.service.DatabaseException;
import com.studymate.service.PdfService;
import com.studymate.service.QuestionDatabase;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/** Main class: creates the window, the sidebar and switches between the four screens. */
public class StudyMateApp extends Application {
    private QuestionDatabase database;
    private final PdfService pdfService = new PdfService();
    private Stage primaryStage;
    private BorderPane rootLayout;

    private DashboardController dashboardController;
    private ReviewerController reviewerController;
    private QuestionEditorController editorController;
    private StudyController studyController;

    private Button dashboardButton;
    private Button reviewersButton;
    private Button editorButton;
    private Button studyButton;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        // Any unexpected error in a button handler ends up here and is shown in a pop-up.
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            UiHelper.runLater(() -> UiHelper.showError("Something went wrong", describe(error)));
        });

        try {
            database = new QuestionDatabase();
        } catch (RuntimeException e) {
            UiHelper.showError("Cannot open the database", describe(e));
            Platform.exit();
            return;
        }

        dashboardController = new DashboardController(this);
        reviewerController = new ReviewerController(this);
        editorController = new QuestionEditorController(this);
        studyController = new StudyController(this);

        rootLayout = new BorderPane();
        rootLayout.getStyleClass().add("main-area");
        rootLayout.setLeft(buildSidebar());

        Scene scene = new Scene(rootLayout, 1150, 740);
        scene.getStylesheets().add(UiHelper.stylesheetUrl());
        stage.setTitle("StudyMate");
        stage.setMinWidth(980);
        stage.setMinHeight(660);
        stage.setScene(scene);
        stage.show();

        showDashboard();
    }

    private String describe(Throwable error) {
        if (error instanceof IllegalArgumentException) {
            return error.getMessage();
        }
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (error instanceof DatabaseException && error.getCause() != null) {
            message += "\nDetails: " + error.getCause().getMessage();
        }
        return message;
    }

    private VBox buildSidebar() {
        Label name = UiHelper.label("StudyMate", "app-name");
        Label tagline = UiHelper.label("Your offline flashcard reviewer", "app-tagline");

        dashboardButton = UiHelper.button("Dashboard", "nav-button");
        reviewersButton = UiHelper.button("My Reviewers", "nav-button");
        editorButton = UiHelper.button("Question Editor", "nav-button");
        studyButton = UiHelper.button("Study Mode", "nav-button");
        for (Button button : List.of(dashboardButton, reviewersButton, editorButton, studyButton)) {
            button.setMaxWidth(Double.MAX_VALUE);
        }
        dashboardButton.setOnAction(e -> showDashboard());
        reviewersButton.setOnAction(e -> showReviewers());
        editorButton.setOnAction(e -> showEditor(null));
        studyButton.setOnAction(e -> showStudy(null));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label footer = UiHelper.label("Data is saved in:\n" + database.getDatabaseFile(), "footer-note");

        VBox sidebar = new VBox(8, name, tagline, new Region(), dashboardButton, reviewersButton,
                editorButton, studyButton, spacer, footer);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(26, 16, 20, 16));
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(240);
        return sidebar;
    }

    private void setActive(Button active) {
        for (Button button : List.of(dashboardButton, reviewersButton, editorButton, studyButton)) {
            button.getStyleClass().remove("nav-button-active");
        }
        active.getStyleClass().add("nav-button-active");
    }

    // ------------------------------------------------ navigation (called by the controllers too)

    public void showDashboard() {
        dashboardController.refresh();
        rootLayout.setCenter(dashboardController.getView());
        setActive(dashboardButton);
    }

    public void showReviewers() {
        reviewerController.refresh();
        rootLayout.setCenter(reviewerController.getView());
        setActive(reviewersButton);
    }

    /** reviewer may be null: then the editor keeps showing the reviewer it had before. */
    public void showEditor(Reviewer reviewer) {
        editorController.refresh(reviewer);
        rootLayout.setCenter(editorController.getView());
        setActive(editorButton);
    }

    /** reviewer may be null: then Study Mode keeps its current state. */
    public void showStudy(Reviewer reviewer) {
        studyController.refresh(reviewer);
        rootLayout.setCenter(studyController.getView());
        setActive(studyButton);
    }

    public QuestionDatabase getDatabase() { return database; }
    public PdfService getPdfService() { return pdfService; }
    public Stage getStage() { return primaryStage; }
}
