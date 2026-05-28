package cn.edu.cuc.class10.entity;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "interactions")
public class Interaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String interactionId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String wordId;

    @Column(nullable = false)
    private String feedback;  // 'known' or 'unknown'

    @Column(nullable = false)
    private Long timestamp;

    // constructors, getters, setters
    public Interaction() {}

    public Interaction(String userId, String wordId, String feedback, Long timestamp) {
        this.userId = userId;
        this.wordId = wordId;
        this.feedback = feedback;
        this.timestamp = timestamp;
    }

    public String getInteractionId() { return interactionId; }
    public void setInteractionId(String interactionId) { this.interactionId = interactionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getWordId() { return wordId; }
    public void setWordId(String wordId) { this.wordId = wordId; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}