package cn.edu.cuc.class10.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String userId;                    // 用户唯一ID（UUID）

    @Column(unique = true, nullable = false)
    private String username;                  // 用户名（唯一）

    @Column(nullable = false)
    private String phase;                     // 学习阶段：primary（小学）| junior（初中）| senior（高中）| non-student

    @Column(nullable = false)
    private String userType;                  // 学习模式：quiz（刷题型）| memory（记忆型）

    @Column(nullable = false)
    private String password;                  // 登录密码（明文存储，简单项目）

    @Column(name = "daily_goal")
    private Integer dailyGoal = 10;           // 每日学习目标单词数（默认10个）

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
}
