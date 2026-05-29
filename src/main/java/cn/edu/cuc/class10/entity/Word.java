package cn.edu.cuc.class10.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "words")
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String wordId;

    @Column(nullable = false, length = 100)
    private String content;

    @Column(length = 100)
    private String partOfSpeech;

    @Column(nullable = false, length = 1000)
    private String translation;

    @Column(length = 100)
    private String phonetic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WordType wordType;

    @Column(length = 20)
    private String phase;

    @Column(length = 50)
    private String userId; // 自建词汇的所属用户ID，考纲词为null

    @Column(length = 2000)
    private String similarMeanings;

    @Column(length = 2000)
    private String similarSpellings;

    @Column(columnDefinition = "TEXT")
    private String extraAttributes;

    @Column(columnDefinition = "TEXT")
    private String phrases;

    @Column(columnDefinition = "TEXT")
    private String sentences;

    public Word() {
    }

    public Word(String content, String partOfSpeech, String translation,
                String phonetic, WordType wordType) {
        this.content = content;
        this.partOfSpeech = partOfSpeech;
        this.translation = translation;
        this.phonetic = phonetic;
        this.wordType = wordType;
    }

    public Word(String content, String partOfSpeech, String translation,
                String phonetic, WordType wordType, String phase) {
        this.content = content;
        this.partOfSpeech = partOfSpeech;
        this.translation = translation;
        this.phonetic = phonetic;
        this.wordType = wordType;
        this.phase = phase;
    }

    public String getWordId() {
        return wordId;
    }

    public void setWordId(String wordId) {
        this.wordId = wordId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPartOfSpeech() {
        return partOfSpeech;
    }

    public void setPartOfSpeech(String partOfSpeech) {
        this.partOfSpeech = partOfSpeech;
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    public String getPhonetic() {
        return phonetic;
    }

    public void setPhonetic(String phonetic) {
        this.phonetic = phonetic;
    }

    public WordType getWordType() {
        return wordType;
    }

    public void setWordType(WordType wordType) {
        this.wordType = wordType;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSimilarMeanings() {
        return similarMeanings;
    }

    public void setSimilarMeanings(String similarMeanings) {
        this.similarMeanings = similarMeanings;
    }

    public String getSimilarSpellings() {
        return similarSpellings;
    }

    public void setSimilarSpellings(String similarSpellings) {
        this.similarSpellings = similarSpellings;
    }

    public String getExtraAttributes() {
        return extraAttributes;
    }

    public void setExtraAttributes(String extraAttributes) {
        this.extraAttributes = extraAttributes;
    }

    public String getPhrases() {
        return phrases;
    }

    public void setPhrases(String phrases) {
        this.phrases = phrases;
    }

    public String getSentences() {
        return sentences;
    }

    public void setSentences(String sentences) {
        this.sentences = sentences;
    }
}
