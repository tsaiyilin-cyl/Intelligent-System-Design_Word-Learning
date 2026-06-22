package cn.edu.cuc.class10.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_word_familiarity")
@IdClass(UserWordFamiliarityId.class)
public class UserWordFamiliarity {

    @Id
    private String userId;                    // 用户ID（复合主键之一）

    @Id
    private String wordId;                    // 单词ID（复合主键之一）

    @Column(nullable = false)
    private Integer familiarity;              // 熟悉度（0~230，越高越熟悉，初始50，可叠加时间衰减）

    @Column(nullable = false)
    private Long lastUpdate;                  // 上次更新熟悉度的时间（毫秒时间戳，用于衰减计算）

    // constructors, getters, setters
    public UserWordFamiliarity() {}

    public UserWordFamiliarity(String userId, String wordId, Integer familiarity, Long lastUpdate) {
        this.userId = userId;
        this.wordId = wordId;
        this.familiarity = familiarity;
        this.lastUpdate = lastUpdate;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getWordId() { return wordId; }
    public void setWordId(String wordId) { this.wordId = wordId; }
    public Integer getFamiliarity() { return familiarity; }
    public void setFamiliarity(Integer familiarity) { this.familiarity = familiarity; }
    public Long getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(Long lastUpdate) { this.lastUpdate = lastUpdate; }
}