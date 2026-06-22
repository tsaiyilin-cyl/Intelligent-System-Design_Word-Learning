package cn.edu.cuc.class10.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * 错题本实体 - 记录用户标记为"不熟悉"的单词
 */
@Entity
@Table(name = "mistake_words")
public class MistakeWord {

    @Id
    @Column(name = "record_id", length = 36)
    private String recordId;                  // 记录唯一ID（UUID）

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;                    // 用户ID

    @Column(name = "word_id", nullable = false, length = 36)
    private String wordId;                    // 单词ID

    @Column(name = "create_time", nullable = false)
    private Long createTime;                  // 加入生词本的时间（毫秒时间戳）

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount;              // 复习次数

    @Column(name = "last_review_time")
    private Long lastReviewTime;              // 最后复习时间（毫秒时间戳）

    public MistakeWord() {}

    public MistakeWord(String userId, String wordId) {
        this.recordId = UUID.randomUUID().toString();
        this.userId = userId;
        this.wordId = wordId;
        this.createTime = System.currentTimeMillis();
        this.reviewCount = 0;
        this.lastReviewTime = null;
    }

    // Getters and Setters
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getWordId() { return wordId; }
    public void setWordId(String wordId) { this.wordId = wordId; }

    public Long getCreateTime() { return createTime; }
    public void setCreateTime(Long createTime) { this.createTime = createTime; }

    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }

    public Long getLastReviewTime() { return lastReviewTime; }
    public void setLastReviewTime(Long lastReviewTime) { this.lastReviewTime = lastReviewTime; }
}
