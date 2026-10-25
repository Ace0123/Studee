package com.studymate.service;

import com.studymate.model.QuizType;
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
    private static final String QUESTION_COLUMNS =
            "id, reviewer_id, question_type, question_text, correct_answer, accepted_answers, "
                    + "option_a, option_b, option_c, option_d, correct_option";

    private final String databaseUrl;
    private final Path databaseFile;

    /** Opens (or creates) the database in the user's home folder: C:\Users\NAME\StudyMate\studymate.db */
    public QuestionDatabase() {
        this(Paths.get(System.getProperty("user.home"), "StudyMate"));
    }

    /** Same, but in a folder of your choice (used for testing). */
    public QuestionDatabase(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new DatabaseException("Could not create the data folder: " + folder, e);
        }
        databaseFile = folder.resolve("studymate.db");
        databaseUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        createOrUpgradeTables(folder);
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

    // ---------------------------------------------------------------- creating / upgrading tables

    private void createOrUpgradeTables(Path folder) {
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

            // Databases made by the first version of StudyMate lack the new columns.
            boolean needsUpgrade = !hasColumn(connection, "questions", "question_type");
            if (needsUpgrade) {
                backupOldDatabase(folder);
            }
            connection.setAutoCommit(false);
            try {
                upgradeColumns(connection);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not create or upgrade the database tables.", e);
        }
    }

    /** Adds the new columns. Existing rows keep their data and get safe default values. */
    private void upgradeColumns(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!hasColumn(connection, "questions", "question_type")) {
                statement.execute("ALTER TABLE questions ADD COLUMN question_type TEXT NOT NULL DEFAULT 'FLASHCARD'");
                // Questions made by the old PDF import as "Fill in the blank: ..." are real fill-in questions.
                statement.execute("UPDATE questions SET question_type = 'FILL_BLANK' "
                        + "WHERE question_text LIKE 'Fill in the blank:%'");
            }
            String[] textColumns = {"accepted_answers", "option_a", "option_b", "option_c", "option_d"};
            for (String column : textColumns) {
                if (!hasColumn(connection, "questions", column)) {
                    statement.execute("ALTER TABLE questions ADD COLUMN " + column + " TEXT NOT NULL DEFAULT ''");
                }
            }
            if (!hasColumn(connection, "questions", "correct_option")) {
                statement.execute("ALTER TABLE questions ADD COLUMN correct_option INTEGER NOT NULL DEFAULT -1");
            }
            if (!hasColumn(connection, "study_sessions", "quiz_type")) {
                statement.execute("ALTER TABLE study_sessions ADD COLUMN quiz_type TEXT NOT NULL DEFAULT 'FLASHCARD'");
            }
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rows.next()) {
                if (column.equalsIgnoreCase(rows.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Before the first upgrade, keeps a copy of the old file (only if it already holds data). */
    private void backupOldDatabase(Path folder) {
        Path backup = folder.resolve("studymate-backup-before-v1.1.db");
        try {
            if (Files.exists(databaseFile) && Files.size(databaseFile) > 0 && !Files.exists(backup)
                    && queryInt("SELECT COUNT(*) FROM reviewers") > 0) {
                Files.copy(databaseFile, backup);
            }
        } catch (IOException e) {
            throw new DatabaseException("Could not back up the old database before upgrading it.", e);
        }
    }

    /** The id of the row that was just inserted on this connection (works with every SQLite driver version). */
    private int lastInsertedId(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT last_insert_rowid()")) {
            rows.next();
            return rows.getInt(1);
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
             PreparedStatement statement = connection.prepareStatement("INSERT INTO reviewers (title) VALUES (?)")) {
            statement.setString(1, clean);
            statement.executeUpdate();
            return new Reviewer(lastInsertedId(connection), clean, 0);
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

    /** All reviewers (sorted by title) with their question count and the date they were last studied. */
    public List<Reviewer> getAllReviewers() {
        String sql = "SELECT r.id, r.title, "
                + "(SELECT COUNT(*) FROM questions q WHERE q.reviewer_id = r.id), "
                + "(SELECT MAX(s.completed_at) FROM study_sessions s WHERE s.reviewer_id = r.id) "
                + "FROM reviewers r ORDER BY r.title COLLATE NOCASE";
        List<Reviewer> result = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(new Reviewer(rows.getInt(1), rows.getString(2), rows.getInt(3), rows.getString(4)));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the reviewers.", e);
        }
        return result;
    }

    // ---------------------------------------------------------------- questions

    private StudyQuestion readQuestion(ResultSet rows) throws SQLException {
        String[] options = {rows.getString(7), rows.getString(8), rows.getString(9), rows.getString(10)};
        return new StudyQuestion(rows.getInt(1), rows.getInt(2), QuizType.fromDatabase(rows.getString(3)),
                rows.getString(4), rows.getString(5), rows.getString(6), options, rows.getInt(11));
    }

    public List<StudyQuestion> getQuestions(int reviewerId) {
        String sql = "SELECT " + QUESTION_COLUMNS + " FROM questions WHERE reviewer_id = ? ORDER BY id";
        List<StudyQuestion> result = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reviewerId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(readQuestion(rows));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the questions.", e);
        }
        return result;
    }

    /** Checks and cleans a question before saving. Throws IllegalArgumentException with a friendly message. */
    private void prepareForSave(StudyQuestion question) {
        String text = question.getQuestionText() == null ? "" : question.getQuestionText().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("The question text cannot be empty.");
        }
        question.setQuestionText(text);
        question.setCorrectAnswer(question.getCorrectAnswer().trim());
        if (question.getType() == QuizType.MULTIPLE_CHOICE && !question.isValidMultipleChoice()) {
            throw new IllegalArgumentException(
                    "A multiple choice question needs four different options and one option marked as correct.");
        }
    }

    /** Sets the 10 values shared by INSERT and UPDATE, starting at parameter number 'first'. */
    private void bindQuestionValues(PreparedStatement statement, StudyQuestion question, int first) throws SQLException {
        statement.setString(first, question.getType().name());
        statement.setString(first + 1, question.getQuestionText());
        statement.setString(first + 2, question.getCorrectAnswer()); // for multiple choice: the correct option's text
        statement.setString(first + 3, question.getAcceptedAnswers().trim());
        for (int i = 0; i < StudyQuestion.OPTION_COUNT; i++) {
            statement.setString(first + 4 + i, question.getOption(i));
        }
        statement.setInt(first + 8, question.getCorrectOption());
    }

    private static final String INSERT_QUESTION = "INSERT INTO questions (reviewer_id, question_type, question_text, "
            + "correct_answer, accepted_answers, option_a, option_b, option_c, option_d, correct_option) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** Saves a new question for its reviewer and returns it with its new id. */
    public StudyQuestion addQuestion(StudyQuestion question) {
        prepareForSave(question);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_QUESTION)) {
            statement.setInt(1, question.getReviewerId());
            bindQuestionValues(statement, question, 2);
            statement.executeUpdate();
            return new StudyQuestion(lastInsertedId(connection), question.getReviewerId(), question.getType(),
                    question.getQuestionText(), question.getCorrectAnswer(), question.getAcceptedAnswers(),
                    question.getOptions(), question.getCorrectOption());
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the question.", e);
        }
    }

    public void updateQuestion(StudyQuestion question) {
        prepareForSave(question);
        String sql = "UPDATE questions SET question_type = ?, question_text = ?, correct_answer = ?, "
                + "accepted_answers = ?, option_a = ?, option_b = ?, option_c = ?, option_d = ?, correct_option = ? "
                + "WHERE id = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindQuestionValues(statement, question, 1);
            statement.setInt(10, question.getId());
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
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_QUESTION)) {
                for (StudyQuestion question : questions) {
                    prepareForSave(question);
                    statement.setInt(1, reviewerId);
                    bindQuestionValues(statement, question, 2);
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

    // ---------------------------------------------------------------- sessions & statistics

    public void saveSession(int reviewerId, QuizType quizType, int totalAnswered, int correct, int incorrect,
                            double percentage) {
        String sql = "INSERT INTO study_sessions (reviewer_id, quiz_type, total_answered, correct_count, "
                + "incorrect_count, percentage, completed_at) VALUES (?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reviewerId);
            statement.setString(2, quizType.name());
            statement.setInt(3, totalAnswered);
            statement.setInt(4, correct);
            statement.setInt(5, incorrect);
            statement.setDouble(6, percentage);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the session result.", e);
        }
    }

    /** Newest sessions first. Pass -1 to get all of them. */
    public List<StudySession> getRecentSessions(int limit) {
        String sql = "SELECT s.id, s.reviewer_id, r.title, s.quiz_type, s.total_answered, s.correct_count, "
                + "s.incorrect_count, s.percentage, s.completed_at "
                + "FROM study_sessions s JOIN reviewers r ON r.id = s.reviewer_id ORDER BY s.id DESC LIMIT ?";
        List<StudySession> result = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new StudySession(rows.getInt(1), rows.getInt(2), rows.getString(3),
                            QuizType.fromDatabase(rows.getString(4)), rows.getInt(5), rows.getInt(6),
                            rows.getInt(7), rows.getDouble(8), rows.getString(9)));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the study history.", e);
        }
        return result;
    }

    public List<StudySession> getAllSessions() { return getRecentSessions(-1); }

    public void deleteAllSessions() {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM study_sessions")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not clear the study history.", e);
        }
    }

    public int getReviewerCount() { return queryInt("SELECT COUNT(*) FROM reviewers"); }
    public int getTotalQuestionCount() { return queryInt("SELECT COUNT(*) FROM questions"); }
    public int getSessionCount() { return queryInt("SELECT COUNT(*) FROM study_sessions"); }

    /** Questions that are complete (have a correct answer, or four options and a correct option). */
    public int getReadyQuestionCount() {
        return queryInt("SELECT COUNT(*) FROM questions WHERE "
                + "(question_type = 'MULTIPLE_CHOICE' AND TRIM(option_a) <> '' AND TRIM(option_b) <> '' "
                + "AND TRIM(option_c) <> '' AND TRIM(option_d) <> '' AND correct_option BETWEEN 0 AND 3) "
                + "OR (question_type <> 'MULTIPLE_CHOICE' AND TRIM(correct_answer) <> '')");
    }

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
