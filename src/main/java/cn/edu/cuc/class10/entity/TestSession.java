package cn.edu.cuc.class10.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * 测试会话实体 - 记录每次测试的结果
 */
@Entity
@Table(name = "test_sessions")
public class TestSession {

    @Id
    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "question_type", length = 50)
    private String questionType;  // en2zh_choice, zh2en_choice, spelling

    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions;

    @Column(name = "correct_count", nullable = false)
    private Integer correctCount;

    @Column(name = "start_time", nullable = false)
    private Long startTime;

    @Column(name = "end_time", nullable = false)
    private Long endTime;

    @Column(name = "create_time")
    private Long createTime;

    public TestSession() {
    }

    public TestSession(String userId, String questionType, Integer totalQuestions,
                       Integer correctCount, Long startTime, Long endTime) {
        this.sessionId = UUID.randomUUID().toString();
        this.userId = userId;
        this.questionType = questionType;
        this.totalQuestions = totalQuestions;
        this.correctCount = correctCount;
        this.startTime = startTime;
        this.endTime = endTime;
        this.createTime = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public Integer getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(Integer totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public Integer getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(Integer correctCount) {
        this.correctCount = correctCount;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }
}
