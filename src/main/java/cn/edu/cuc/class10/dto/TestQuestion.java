package cn.edu.cuc.class10.dto;

import java.util.List;

public class TestQuestion {
    private String questionId;
    private String type;
    private String wordId;
    private String content;
    private List<String> options;
    private String correctAnswer;
    private String partOfSpeech;  // 新增：词性中文
    private String translation;   // 新增：释义

    public TestQuestion() {}

    // getters and setters
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getWordId() { return wordId; }
    public void setWordId(String wordId) { this.wordId = wordId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getPartOfSpeech() { return partOfSpeech; }
    public void setPartOfSpeech(String partOfSpeech) { this.partOfSpeech = partOfSpeech; }
    public String getTranslation() { return translation; }
    public void setTranslation(String translation) { this.translation = translation; }
}