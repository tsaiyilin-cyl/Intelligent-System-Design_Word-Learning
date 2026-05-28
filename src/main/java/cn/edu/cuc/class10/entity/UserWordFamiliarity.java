package cn.edu.cuc.class10.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_word_familiarity")
@IdClass(UserWordFamiliarityId.class)
public class UserWordFamiliarity {

    @Id
    private String userId;

    @Id
    private String wordId;

    @Column(nullable = false)
    private Integer familiarity;  // 0~100

    @Column(nullable = false)
    private Long lastUpdate;

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