package com.studymate.service;

import com.studymate.model.Reviewer;
import com.studymate.model.StudyQuestion;
import com.studymate.model.StudySession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** All SQLite work. Every method opens and closes its own connection (try-with-resources). */
public class QuestionDatabase {
    private final String databaseUrl;
    private final Path databaseFile;

    public QuestionDatabase() {
        // C:\Users\<you>\StudyMate\studymate.db
        Path folder = Paths.get(System.getProperty("user.home"), "StudyMate");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new DatabaseException("Could not create the data folder: " + folder, e);
        }
        databaseFile = folder.resolve("studymate.db");
        databaseUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        createTables();
    }

    public Path getDatabaseFile() { return databaseFile; }

    /** Opens a connection and turns on foreign key checks (SQLite has them off by default). */
    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(databaseUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    private void createTables() {
        String reviewers = "CREATE TABLE IF NOT EXISTS reviewers ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL UNIQUE COLLATE NOCASE)";
        String questions = "CREATE TABLE IF NOT EXISTS questions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "reviewer_id INTEGER NOT NULL, "
                + "question_text TEXT NOT NULL, "
                + "correct_answer TEXT NOT NULL DEFAULT '', "
                + "FOREIGN KEY (reviewer_id) REFERENCES reviewers(id) ON DELETE CASCADE)";
        String sessions = "CREATE TABLE IF NOT EXISTS study_sessions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "reviewer_id INTEGER NOT NULL, "
                + "total_answered INTEGER NOT NULL, "
                + "correct_count INTEGER NOT NULL, "
                + "incorrect_count INTEGER NOT NULL, "
                + "percentage REAL NOT NULL, "
                + "completed_at TEXT NOT NULL, "
                + "FOREIGN KEY (reviewer_id) REFERENCES reviewers(id) ON DELETE CASCADE)";
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute(reviewers);
            statement.execute(questions);
            statement.execute(sessions);
        } catch (SQLException e) {
            throw new DatabaseException("Could not create the database tables.", e);
        }
    }

    // ---------------------------------------------------------------- reviewers

    /** Checks the title; throws IllegalArgumentException with a friendly message if invalid. */
    private String validateTitle(String title, int ignoreReviewerId) {
        String clean = title == null ? "" : title.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("The reviewer title cannot be empty.");
        }
        if (clean.length() > 100) {
            throw new IllegalArgumentException("The reviewer title is too long (maximum 100 characters).");
        }
        int duplicates = queryInt("SELECT COUNT(*) FROM reviewers WHERE LOWER(title) = LOWER(?) AND id <> ?",
                clean, ignoreReviewerId);
        if (duplicates > 0) {
            throw new IllegalArgumentException("A reviewer named \"" + clean + "\" already exists.");
        }
        return clean;
    }

    public Reviewer createReviewer(String title) {
        String clean = validateTitle(title, -1);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO reviewers (title) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, clean);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return new Reviewer(keys.getInt(1), clean, 0);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not create the reviewer.", e);
        }
    }

    public void renameReviewer(int reviewerId, String newTitle) {
        String clean = validateTitle(newTitle, reviewerId);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE reviewers SET title = ? WHERE id = ?")) {
            statement.setString(1, clean);
            statement.setInt(2, reviewerId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not rename the reviewer.", e);
        }
    }

    /** Deletes the reviewer together with its questions and saved sessions, in one transaction. */
    public void deleteReviewer(int reviewerId) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                runDelete(connection, "DELETE FROM study_sessions WHERE reviewer_id = ?", reviewerId);
                runDelete(connection, "DELETE FROM questions WHERE reviewer_id = ?", reviewerId);
                runDelete(connection, "DELETE FROM reviewers WHERE id = ?", reviewerId);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete the reviewer.", e);
        }
    }

    private void runDelete(Connection connection, String sql, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    public List<Reviewer> getAllReviewers() {
        String sql = "SELECT r.id, r.title, COUNT(q.id) FROM reviewers r "
                + "LEFT JOIN questions q ON q.reviewer_id = r.id "
                + "GROUP BY r.id, r.title ORDER BY r.title COLLATE NOCASE";
        List<Reviewer> result = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(new Reviewer(rows.getInt(1), rows.getString(2), rows.getInt(3)));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the reviewers.", e);
        }
        return result;
    }

    // ---------------------------------------------------------------- questions

    public List<StudyQuestion> getQuestions(int reviewerId) {
        String sql = "SELECT id, reviewer_id, question_text, correct_answer FROM questions "
                + "WHERE reviewer_id = ? ORDER BY id";
        List<StudyQuestion> result = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reviewerId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new StudyQuestion(rows.getInt(1), rows.getInt(2), rows.getString(3), rows.getString(4)));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the questions.", e);
        }
        return result;
    }

    public StudyQuestion addQuestion(int reviewerId, String questionText, String correctAnswer) {
        String question = requireQuestionText(questionText);
        String answer = correctAnswer == null ? "" : correctAnswer.trim();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO questions (reviewer_id, question_text, correct_answer) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, reviewerId);
            statement.setString(2, question);
            statement.setString(3, answer);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return new StudyQuestion(keys.getInt(1), reviewerId, question, answer);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the question.", e);
        }
    }

    public void updateQuestion(StudyQuestion question) {
        String text = requireQuestionText(question.getQuestionText());
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE questions SET question_text = ?, correct_answer = ? WHERE id = ?")) {
            statement.setString(1, text);
            statement.setString(2, question.getCorrectAnswer().trim());
            statement.setInt(3, question.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not update the question.", e);
        }
    }

    public void deleteQuestion(int questionId) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM questions WHERE id = ?")) {
            statement.setInt(1, questionId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete the question.", e);
        }
    }

    /** Saves many questions at once (used by PDF import). Returns how many were saved. */
    public int addQuestions(int reviewerId, List<StudyQuestion> questions) {
        String sql = "INSERT INTO questions (reviewer_id, question_text, correct_answer) VALUES (?, ?, ?)";
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (StudyQuestion question : questions) {
                    statement.setInt(1, reviewerId);
                    statement.setString(2, requireQuestionText(question.getQuestionText()));
                    statement.setString(3, question.getCorrectAnswer().trim());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
                return questions.size();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not import the questions.", e);
        }
    }

    private String requireQuestionText(String text) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("The question text cannot be empty.");
        }
        return clean;
    }

    // ---------------------------------------------------------------- sessions & statistics

    public void saveSession(int reviewerId, int totalAnswered, int correct, int incorrect, double percentage) {
        String sql = "INSERT INTO study_sessions (reviewer_id, total_answered, correct_count, incorrect_count, "
                + "percentage, completed_at) VALUES (?, ?, ?, ?, ?, datetime('now', 'localtime'))";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reviewerId);
            statement.setInt(2, totalAnswered);
            statement.setInt(3, correct);
            statement.setInt(4, incorrect);
            statement.setDouble(5, percentage);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the session result.", e);
        }
    }

    public List<StudySession> getRecentSessions(int limit) {
        String sql = "SELECT s.id, s.reviewer_id, r.title, s.total_answered, s.correct_count, "
                + "s.incorrect_count, s.percentage, s.completed_at "
                + "FROM study_sessions s JOIN reviewers r ON r.id = s.reviewer_id ORDER BY s.id DESC LIMIT ?";
        List<StudySession> result = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new StudySession(rows.getInt(1), rows.getInt(2), rows.getString(3), rows.getInt(4),
                            rows.getInt(5), rows.getInt(6), rows.getDouble(7), rows.getString(8)));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the study history.", e);
        }
        return result;
    }

    public int getReviewerCount() { return queryInt("SELECT COUNT(*) FROM reviewers"); }
    public int getTotalQuestionCount() { return queryInt("SELECT COUNT(*) FROM questions"); }
    public int getSessionCount() { return queryInt("SELECT COUNT(*) FROM study_sessions"); }

    public double getAverageScore() {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT AVG(percentage) FROM study_sessions");
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getDouble(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Could not calculate the average score.", e);
        }
    }

    /** Runs a "SELECT one number" query with optional ? parameters. */
    private int queryInt(String sql, Object... parameters) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database read failed.", e);
        }
    }
}
