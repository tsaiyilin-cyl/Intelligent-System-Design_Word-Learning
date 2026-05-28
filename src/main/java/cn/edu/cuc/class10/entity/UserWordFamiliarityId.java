package cn.edu.cuc.class10.entity;
//复合主键
import java.io.Serializable;
import java.util.Objects;

public class UserWordFamiliarityId implements Serializable {
    private String userId;
    private String wordId;

    public UserWordFamiliarityId() {}
    public UserWordFamiliarityId(String userId, String wordId) {
        this.userId = userId;
        this.wordId = wordId;
    }

    // getters, setters, equals, hashCode
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getWordId() { return wordId; }
    public void setWordId(String wordId) { this.wordId = wordId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserWordFamiliarityId that = (UserWordFamiliarityId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(wordId, that.wordId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, wordId);
    }
}