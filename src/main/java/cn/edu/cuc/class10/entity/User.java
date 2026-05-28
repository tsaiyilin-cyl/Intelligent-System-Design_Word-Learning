package cn.edu.cuc.class10.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String userId;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String phase; // 'primary' | 'junior' | 'senior'

    @Column(nullable = false)
    private String userType; // 'quiz' | 'memory'

    @Column(nullable = false)
    private String password;

    // 学习计划相关字段
    @Column(name = "daily_goal")
    private Integer dailyGoal = 10; // 每日学习目标单词数

    @Column(name = "total_words")
    private Integer totalWords = 0; // 已学习单词总数

    @Column(name = "mastered_words")
    private Integer masteredWords = 0; // 已掌握单词数

    @Column(name = "study_streak")
    private Integer studyStreak = 0; // 连续学习天数

    @Column(name = "last_study_date")
    private String lastStudyDate; // 最后学习日期

    public User() {
    }

    public User(String username, String phase, String userType, String password) {
        this.username = username;
        this.phase = phase;
        this.userType = userType;
        this.password = password;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getDailyGoal() {
        return dailyGoal;
    }

    public void setDailyGoal(Integer dailyGoal) {
        this.dailyGoal = dailyGoal;
    }

    public Integer getTotalWords() {
        return totalWords;
    }

    public void setTotalWords(Integer totalWords) {
        this.totalWords = totalWords;
    }

    public Integer getMasteredWords() {
        return masteredWords;
    }

    public void setMasteredWords(Integer masteredWords) {
        this.masteredWords = masteredWords;
    }

    public Integer getStudyStreak() {
        return studyStreak;
    }

    public void setStudyStreak(Integer studyStreak) {
        this.studyStreak = studyStreak;
    }

    public String getLastStudyDate() {
        return lastStudyDate;
    }

    public void setLastStudyDate(String lastStudyDate) {
        this.lastStudyDate = lastStudyDate;
    }
}
