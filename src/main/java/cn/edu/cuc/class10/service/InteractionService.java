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

    @Autowired
    private WordService wordService;

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
        Long lastUpdate = familiarity.getLastUpdate();
        // 先对存储的熟悉度施加时间衰减，使衰减效果持久化
        int decayedBase = wordService.applyDecay(oldF, lastUpdate);
        // 答对×2，答错×0.58 (范围0~230)
        int newF;
        if ("known".equals(feedback)) {
            newF = Math.min(230, decayedBase * 2);
        } else {
            newF = (int) (decayedBase * 0.58);
        }

        familiarity.setFamiliarity(newF);
        familiarity.setLastUpdate(now);
        familiarityRepository.save(familiarity);
    }

    /**
     * 获取某个用户对某个单词的存储熟悉度（原始值，未经时间衰减）
     * 默认返回 50（未学习过的单词）
     */
    public int getFamiliarity(String userId, String wordId) {
        return familiarityRepository.findByUserIdAndWordId(userId, wordId)
                .map(UserWordFamiliarity::getFamiliarity)
                .orElse(50);
    }
}