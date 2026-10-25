package com.studymate.model;

/** The saved result of one finished study session. */
public class StudySession {
    private final int id;
    private final int reviewerId;
    private final String reviewerTitle;
    private final QuizType quizType;
    private final int totalAnswered;
    private final int correctCount;
    private final int incorrectCount;
    private final double percentage;
    private final String completedAt;

    public StudySession(int id, int reviewerId, String reviewerTitle, QuizType quizType, int totalAnswered,
                        int correctCount, int incorrectCount, double percentage, String completedAt) {
        this.id = id;
        this.reviewerId = reviewerId;
        this.reviewerTitle = reviewerTitle;
        this.quizType = quizType;
        this.totalAnswered = totalAnswered;
        this.correctCount = correctCount;
        this.incorrectCount = incorrectCount;
        this.percentage = percentage;
        this.completedAt = completedAt;
    }

    public int getId() { return id; }
    public int getReviewerId() { return reviewerId; }
    public String getReviewerTitle() { return reviewerTitle; }
    public QuizType getQuizType() { return quizType; }
    public int getTotalAnswered() { return totalAnswered; }
    public int getCorrectCount() { return correctCount; }
    public int getIncorrectCount() { return incorrectCount; }
    public double getPercentage() { return percentage; }
    public String getCompletedAt() { return completedAt; }
}
