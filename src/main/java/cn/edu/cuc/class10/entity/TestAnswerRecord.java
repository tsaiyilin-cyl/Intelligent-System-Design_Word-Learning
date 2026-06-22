package cn.edu.cuc.class10.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * 测试答题记录实体 - 记录每次测试中每道题的答题详情
 */
@Entity
@Table(name = "test_answer_records")
public class TestAnswerRecord {

    @Id
    @Column(name = "record_id", length = 36)
    private String recordId;                  // 答题记录唯一ID（UUID）

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;                 // 关联的测试会话ID（外键→test_sessions）

    @Column(name = "question_index", nullable = false)
    private Integer questionIndex;            // 题目序号（从0开始）

    @Column(name = "word_id", length = 36)
    private String wordId;                    // 单词ID

    @Column(name = "question_type", length = 50)
    private String questionType;              // 题型：en2zh_choice / zh2en_choice / spelling

    @Column(name = "question_content", columnDefinition = "TEXT")
    private String questionContent;           // 题目内容

    @Column(name = "correct_answer", columnDefinition = "TEXT")
    private String correctAnswer;             // 正确答案

    @Column(name = "user_answer", columnDefinition = "TEXT")
    private String userAnswer;                // 用户答案

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;                // 是否正确

    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;               // 选项列表（JSON格式，选择题使用）

    public TestAnswerRecord() {
    }

    public TestAnswerRecord(String sessionId, Integer questionIndex, String wordId,
                            String questionType, String questionContent, String correctAnswer,
                            String userAnswer, Boolean isCorrect, String optionsJson) {
        this.recordId = UUID.randomUUID().toString();
        this.sessionId = sessionId;
        this.questionIndex = questionIndex;
        this.wordId = wordId;
        this.questionType = questionType;
        this.questionContent = questionContent;
        this.correctAnswer = correctAnswer;
        this.userAnswer = userAnswer;
        this.isCorrect = isCorrect;
        this.optionsJson = optionsJson;
    }

    // Getters and Setters
    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getQuestionIndex() {
        return questionIndex;
    }

    public void setQuestionIndex(Integer questionIndex) {
        this.questionIndex = questionIndex;
    }

    public String getWordId() {
        return wordId;
    }

    public void setWordId(String wordId) {
        this.wordId = wordId;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public String getQuestionContent() {
        return questionContent;
    }

    public void setQuestionContent(String questionContent) {
        this.questionContent = questionContent;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public void setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
    }
}
