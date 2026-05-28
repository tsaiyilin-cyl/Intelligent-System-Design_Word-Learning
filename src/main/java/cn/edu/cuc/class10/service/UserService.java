package cn.edu.cuc.class10.service;

import cn.edu.cuc.class10.entity.User;
import cn.edu.cuc.class10.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    /**
     * 用户注册
     * @param username 用户名
     * @param password 密码
     * @param phase 学习阶段
     * @param userType 用户类型
     * @return 注册结果消息
     */
    public String register(String username, String password, String phase, String userType) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(username)) {
            return "用户名已存在";
        }
        
        // 创建新用户并保存
        User newUser = new User(username, phase, userType, password);
        userRepository.save(newUser);
        return "注册成功";
    }

    /**
     * 用户登录
     * @param username 用户名
     * @param password 密码
     * @return 用户对象，如果登录失败返回null
     */
    public User login(String username, String password) {
        // 查询用户
        java.util.Optional<User> userOpt = userRepository.findByUsername(username);
        
        if (userOpt.isEmpty()) {
            // 用户不存在
            return null;
        }
        
        User user = userOpt.get();
        
        // 验证密码
        if (!user.getPassword().equals(password)) {
            // 密码错误
            return null;
        }
        
        // 登录成功
        return user;
    }

    /**
     * 用户登出
     * @param username 用户名
     * @return 登出成功消息
     */
    public String logout(String username) {
        // 验证用户是否存在
        if (!userRepository.existsByUsername(username)) {
            return "用户不存在";
        }
        
        // 这里可以添加清理session等逻辑
        return "登出成功";
    }

    /**
     * 根据用户名获取用户信息
     * @param username 用户名
     * @return 用户对象
     */
    public User getUserByUsername(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        return userOpt.orElse(null);
    }

    /**
     * 更新用户信息
     * @param userId 用户ID
     * @param username 新用户名
     * @param password 新密码
     * @param phase 学习阶段
     * @param userType 用户类型
     * @param dailyGoal 每日学习目标
     * @return 更新结果消息
     */
    public String updateUser(String userId, String username, String password, String phase, String userType, Integer dailyGoal) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return "用户不存在";
        }
        
        User user = userOpt.get();
        
        // 如果修改了用户名，检查是否与其他用户冲突
        if (!user.getUsername().equals(username) && userRepository.existsByUsername(username)) {
            return "用户名已被使用";
        }
        
        // 更新用户信息
        user.setUsername(username);
        if (password != null && !password.isEmpty()) {
            user.setPassword(password);
        }
        if (phase != null) {
            user.setPhase(phase);
        }
        if (userType != null) {
            user.setUserType(userType);
        }
        if (dailyGoal != null) {
            user.setDailyGoal(dailyGoal);
        }
        
        userRepository.save(user);
        return "更新成功";
    }

    /**
     * 删除用户
     * @param userId 用户ID
     * @return 删除结果消息
     */
    public String deleteUser(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return "用户不存在";
        }
        
        userRepository.deleteById(userId);
        return "删除成功";
    }
}
