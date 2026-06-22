package cn.edu.cuc.class10.dto;

import java.util.List;

public class TestQuestion {
    private String questionId;                  // 题目唯一ID（UUID）
    private String type;                        // 题型：en2zh_choice / zh2en_choice / spelling
    private String wordId;                      // 关联的单词ID
    private String content;                     // 题目内容（英文或中文）
    private List<String> options;               // 选项列表（选择题使用，拼写题为null）
    private String correctAnswer;               // 正确答案
    private String partOfSpeech;                // 词性中文（如"名词"、"动词"）
    private String translation;                 // 中文释义

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