package cn.edu.cuc.class10.controller;

import cn.edu.cuc.class10.entity.User;
import cn.edu.cuc.class10.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册接口
     */
    @GetMapping("/register")
    public Map<String, Object> register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String phase,
            @RequestParam String userType) {
        
        Map<String, Object> result = new HashMap<>();
        
        // 调用Service层处理注册逻辑
        String message = userService.register(username, password, phase, userType);
        
        if ("注册成功".equals(message)) {
            result.put("code", 200);
            result.put("message", message);
        } else {
            result.put("code", 400);
            result.put("message", message);
        }
        
        return result;
    }

    /**
     * 用户登录接口
     */
    @GetMapping("/login")
    public Map<String, Object> login(
            @RequestParam String username,
            @RequestParam String password) {
        
        Map<String, Object> result = new HashMap<>();
        
        // 调用Service层处理登录逻辑
        User user = userService.login(username, password);
        
        if (user != null) {
            result.put("code", 200);
            result.put("message", "登录成功");
            result.put("data", user);
        } else {
            result.put("code", 401);
            result.put("message", "用户名或密码错误");
        }
        
        return result;
    }

    /**
     * 用户登出接口
     * GET /api/logout
     */
    @GetMapping("/logout")
    public Map<String, Object> logout(@RequestParam String username) {
        Map<String, Object> result = new HashMap<>();

        // 调用Service层处理登出逻辑
        String message = userService.logout(username);
        
        result.put("code", 200);
        result.put("message", message);
        result.put("username", username);
        
        return result;
    }

    /**
     * 获取用户信息接口
     */
    @GetMapping("/user/info")
    public Map<String, Object> getUserInfo(@RequestParam String username) {
        Map<String, Object> result = new HashMap<>();
        
        User user = userService.getUserByUsername(username);
        
        if (user != null) {
            result.put("code", 200);
            result.put("message", "获取成功");
            result.put("data", user);
        } else {
            result.put("code", 404);
            result.put("message", "用户不存在");
        }
        
        return result;
    }

    /**
     * 更新用户信息接口
     */
    @PostMapping("/user/update")
    public Map<String, Object> updateUser(
            @RequestParam String userId,
            @RequestParam String username,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String phase,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) Integer dailyGoal) {
        
        Map<String, Object> result = new HashMap<>();
        
        String message = userService.updateUser(userId, username, password, phase, userType, dailyGoal);
        
        if ("更新成功".equals(message)) {
            result.put("code", 200);
            result.put("message", message);
            // 返回更新后的用户信息
            User updatedUser = userService.getUserByUsername(username);
            result.put("data", updatedUser);
        } else {
            result.put("code", 400);
            result.put("message", message);
        }
        
        return result;
    }

    /**
     * 删除用户接口
     */
    @DeleteMapping("/user/delete")
    public Map<String, Object> deleteUser(@RequestParam String userId) {
        Map<String, Object> result = new HashMap<>();
        
        String message = userService.deleteUser(userId);
        
        if ("删除成功".equals(message)) {
            result.put("code", 200);
            result.put("message", message);
        } else {
            result.put("code", 400);
            result.put("message", message);
        }
        
        return result;
    }
}
