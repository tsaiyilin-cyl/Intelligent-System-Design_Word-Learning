package cn.edu.cuc.class10.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "words")
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String wordId;                    // 单词唯一ID（UUID）

    @Column(nullable = false, length = 100)
    private String content;                   // 单词内容（英文，如 "frog"）

    @Column(length = 100)
    private String partOfSpeech;              // 词性（NOUN / VERB / ADJECTIVE 等）

    @Column(nullable = false, length = 1000)
    private String translation;               // 中文释义

    @Column(length = 100)
    private String phonetic;                  // 音标（如 ˈfrɒɡ）

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WordType wordType;                // 单词类型：SYLLABUS（考纲词）/ CUSTOM（自建词）

    @Column(length = 20)
    private String phase;                     // 所属学段：primary / junior / senior

    @Column(length = 50)
    private String userId;                    // 自建词汇的所属用户ID，考纲词为null

    @Column(length = 2000)
    private String similarMeanings;           // 相似词义群（JSON数组，存储word_id和相似度）

    @Column(length = 2000)
    private String similarSpellings;          // 相似拼写群（JSON数组，存储word_id和编辑距离）

    @Column(columnDefinition = "TEXT")
    private String extraAttributes;           // 额外属性（保留字段，暂未使用）

    @Column(columnDefinition = "TEXT")
    private String phrases;                   // 常用短语（自建词使用）

    @Column(columnDefinition = "TEXT")
    private String sentences;                 // 例句（自建词使用）

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
