package cn.edu.cuc.class10.dto;

import java.util.List;
import java.util.Map;

public class DashboardDataResponse {
    private int totalWords;          // 用户总词汇数（考纲范围内+自建）
    private int masteredWords;       // 掌握单词数（熟悉度>=70）
    private double averageAccuracy;  // 近7天平均正确率（百分比）
    private List<DailyAccuracy> recentAccuracy; // 近7天每日正确率
    private List<TestRecord> recentTests;       // 最近5次测试记录
    private List<MistakeWord> topMistakes;      // 易错词TOP5

    // Getters and Setters
    public int getTotalWords() { return totalWords; }
    public void setTotalWords(int totalWords) { this.totalWords = totalWords; }

    public int getMasteredWords() { return masteredWords; }
    public void setMasteredWords(int masteredWords) { this.masteredWords = masteredWords; }

    public double getAverageAccuracy() { return averageAccuracy; }
    public void setAverageAccuracy(double averageAccuracy) { this.averageAccuracy = averageAccuracy; }

    public List<DailyAccuracy> getRecentAccuracy() { return recentAccuracy; }
    public void setRecentAccuracy(List<DailyAccuracy> recentAccuracy) { this.recentAccuracy = recentAccuracy; }

    public List<TestRecord> getRecentTests() { return recentTests; }
    public void setRecentTests(List<TestRecord> recentTests) { this.recentTests = recentTests; }

    public List<MistakeWord> getTopMistakes() { return topMistakes; }
    public void setTopMistakes(List<MistakeWord> topMistakes) { this.topMistakes = topMistakes; }

    public static class DailyAccuracy {
        private String date;   // 格式 yyyy-MM-dd
        private double accuracy;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public double getAccuracy() { return accuracy; }
        public void setAccuracy(double accuracy) { this.accuracy = accuracy; }
    }

    public static class TestRecord {
        private String sessionId;
        private String questionType;
        private int total;
        private int correct;
        private double accuracy;
        private long endTime;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }

        public int getCorrect() { return correct; }
        public void setCorrect(int correct) { this.correct = correct; }

        public double getAccuracy() { return accuracy; }
        public void setAccuracy(double accuracy) { this.accuracy = accuracy; }

        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
    }

    public static class MistakeWord {
        private String wordId;
        private String wordContent;
        private int mistakeCount;

        public String getWordId() { return wordId; }
        public void setWordId(String wordId) { this.wordId = wordId; }

        public String getWordContent() { return wordContent; }
        public void setWordContent(String wordContent) { this.wordContent = wordContent; }

        public int getMistakeCount() { return mistakeCount; }
        public void setMistakeCount(int mistakeCount) { this.mistakeCount = mistakeCount; }
    }
}