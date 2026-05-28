package cn.edu.cuc.class10.dto;

/**
 * 提交答案请求DTO（前端→后端）
 */
public class SubmitAnswerRequest {
    private String userId;
    private String wordId;
    private String userAnswer;
    private String correctAnswer;
    private long timestamp;

    public SubmitAnswerRequest() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getWordId() { return wordId; }
    public void setWordId(String wordId) { this.wordId = wordId; }

    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}