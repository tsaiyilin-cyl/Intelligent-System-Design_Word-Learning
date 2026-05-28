package cn.edu.cuc.class10.service;

import cn.edu.cuc.class10.entity.Interaction;
import cn.edu.cuc.class10.entity.UserWordFamiliarity;
import cn.edu.cuc.class10.repository.InteractionRepository;
import cn.edu.cuc.class10.repository.UserWordFamiliarityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class InteractionService {

    @Autowired
    private InteractionRepository interactionRepository;

    @Autowired
    private UserWordFamiliarityRepository familiarityRepository;

    /**
     * 记录一次交互并更新熟悉度
     * @param userId 用户ID
     * @param wordId 单词ID
     * @param feedback "known" 或 "unknown"
     */
    @Transactional
    public void recordInteraction(String userId, String wordId, String feedback) {
        // 1. 保存交互记录
        Long now = System.currentTimeMillis();
        Interaction interaction = new Interaction(userId, wordId, feedback, now);
        interactionRepository.save(interaction);

        // 2. 获取或初始化熟悉度
        UserWordFamiliarity familiarity = familiarityRepository.findByUserIdAndWordId(userId, wordId)
                .orElse(new UserWordFamiliarity(userId, wordId, 50, now)); // 初始熟悉度50

        int oldF = familiarity.getFamiliarity();
        // 增量规则：known +10, unknown -15 (范围0~100)
        int delta = "known".equals(feedback) ? 10 : -15;
        int newF = Math.max(0, Math.min(100, oldF + delta));

        // 3. 时间衰减：如果距上次交互超过24小时，额外减2
        Long lastTimestamp = interactionRepository.findLastTimestampByUserAndWord(userId, wordId)
                .orElse(null);
        if (lastTimestamp != null && (now - lastTimestamp) > 86400000L) {
            newF = Math.max(0, newF - 2);
        }

        familiarity.setFamiliarity(newF);
        familiarity.setLastUpdate(now);
        familiarityRepository.save(familiarity);
    }

    /**
     * 获取某个用户对某个单词的熟悉度
     */
    public int getFamiliarity(String userId, String wordId) {
        return familiarityRepository.findByUserIdAndWordId(userId, wordId)
                .map(UserWordFamiliarity::getFamiliarity)
                .orElse(50);
    }
}