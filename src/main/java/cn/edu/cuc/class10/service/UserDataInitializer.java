package cn.edu.cuc.class10.service;

import cn.edu.cuc.class10.entity.User;
import cn.edu.cuc.class10.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * 用户数据初始化组件
 * 应用启动时自动生成50条模拟用户数据
 */
@Component
public class UserDataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(String... args) throws Exception {
        // 检查是否已有数据
        long count = userRepository.count();
        if (count > 0) {
            System.out.println("数据库中已有 " + count + " 条用户数据，跳过初始化");
            return;
        }

        // 1. 创建默认 admin 用户
        createAdminUser();

        // 2. 生成模拟用户
        System.out.println("开始生成50条模拟用户数据...");
        generateMockUsers(50);
        System.out.println("模拟用户数据生成完成！");
    }

    /**
     * 创建默认管理员账户 (admin / 123456)
     */
    private void createAdminUser() {
        if (userRepository.findByUsername("admin").isPresent()) {
            System.out.println("admin 用户已存在，跳过创建");
            return;
        }

        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword("123456");
        admin.setPhase("senior");
        admin.setUserType("memory");
        admin.setDailyGoal(20);
        userRepository.save(admin);
        System.out.println("admin 用户创建完成 (admin / 123456)");
    }

    /**
     * 生成模拟用户数据
     * @param numUsers 用户数量
     */
    private void generateMockUsers(int numUsers) {
        Random random = new Random();
        
        // 用户名前缀列表
        String[] namePrefixes = {"student", "learner", "user", "pupil", "scholar"};
        
        // 学习阶段数组
        String[] phases = {"primary", "junior", "senior"};
        
        // 用户类型数组
        String[] userTypes = {"quiz", "memory"};

        for (int i = 1; i <= numUsers; i++) {
            User user = new User();
            
            // 生成用户名
            String prefix = namePrefixes[random.nextInt(namePrefixes.length)];
            String username = prefix + "_" + String.format("%03d", i);
            user.setUsername(username);
            
            // 设置密码（统一默认密码）
            user.setPassword("123456");
            
            // 随机选择学习阶段
            user.setPhase(phases[random.nextInt(phases.length)]);
            
            // 随机选择用户类型
            user.setUserType(userTypes[random.nextInt(userTypes.length)]);
            
            // 设置每日学习目标（5-25之间的随机值）
            user.setDailyGoal(random.nextInt(20) + 5);
            
            // 保存用户
            userRepository.save(user);
            
            if (i % 10 == 0) {
                System.out.println("已生成 " + i + " 条用户数据...");
            }
        }
    }
}
