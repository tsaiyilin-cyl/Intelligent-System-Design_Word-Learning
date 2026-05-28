package cn.edu.cuc.class10.dto;

public class GenerateTestRequest {
    private String userId;
    private int count;
    private String questionType;  // 新增: "en2zh", "zh2en", "spelling"

    public GenerateTestRequest() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
}