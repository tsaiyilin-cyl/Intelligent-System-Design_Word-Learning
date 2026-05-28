package cn.edu.cuc.class10.service;

import cn.edu.cuc.class10.entity.User;
import cn.edu.cuc.class10.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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

        System.out.println("开始生成50条模拟用户数据...");
        generateMockUsers(50);
        System.out.println("模拟用户数据生成完成！");
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
            
            // 设置学习计划相关字段
            user.setDailyGoal(random.nextInt(20) + 5); // 5-25之间的随机值
            user.setTotalWords(random.nextInt(500)); // 0-500之间的随机值
            user.setMasteredWords(random.nextInt(user.getTotalWords())); // 小于总学习数
            user.setStudyStreak(random.nextInt(30)); // 0-30天
            
            // 设置最后学习日期（最近30天内）
            LocalDate lastStudyDate = LocalDate.now().minusDays(random.nextInt(30));
            user.setLastStudyDate(lastStudyDate.toString());
            
            // 保存用户
            userRepository.save(user);
            
            if (i % 10 == 0) {
                System.out.println("已生成 " + i + " 条用户数据...");
            }
        }
    }
}
