package cn.edu.cuc.class10.controller;

import cn.edu.cuc.class10.entity.MistakeWord;
import cn.edu.cuc.class10.entity.Word;
import cn.edu.cuc.class10.entity.UserWordFamiliarity;
import cn.edu.cuc.class10.repository.MistakeWordRepository;
import cn.edu.cuc.class10.repository.UserWordFamiliarityRepository;
import cn.edu.cuc.class10.repository.WordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mistake")
public class MistakeController {

    @Autowired
    private MistakeWordRepository mistakeWordRepository;

    @Autowired
    private WordRepository wordRepository;

    @Autowired
    private UserWordFamiliarityRepository familiarityRepository;

    /**
     * 获取用户的生词本列表
     */
    @GetMapping("/list")
    public Map<String, Object> getList(@RequestParam String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<MistakeWord> mistakes = mistakeWordRepository.findByUserIdOrderByCreateTimeDesc(userId);
            
            // 关联单词信息
            List<Map<String, Object>> data = mistakes.stream().map(mistake -> {
                Map<String, Object> item = new HashMap<>();
                item.put("recordId", mistake.getRecordId());
                item.put("wordId", mistake.getWordId());
                item.put("createTime", mistake.getCreateTime());
                item.put("reviewCount", mistake.getReviewCount());
                
                // 获取单词详情
                wordRepository.findById(mistake.getWordId()).ifPresent(word -> {
                    Map<String, Object> wordInfo = new HashMap<>();
                    wordInfo.put("content", word.getContent());
                    wordInfo.put("translation", word.getTranslation());
                    wordInfo.put("phonetic", word.getPhonetic());
                    item.put("word", wordInfo);
                });
                
                return item;
            }).collect(Collectors.toList());
            
            result.put("code", 200);
            result.put("data", data);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 添加单词到生词本
     */
    @PostMapping("/add")
    public Map<String, Object> add(@RequestBody Map<String, String> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            String userId = payload.get("userId");
            String wordId = payload.get("wordId");
            
            if (userId == null || wordId == null) {
                result.put("code", 400);
                result.put("message", "参数缺失");
                return result;
            }
            
            // 检查是否已存在
            Optional<MistakeWord> existing = mistakeWordRepository.findByUserIdAndWordId(userId, wordId);
            if (existing.isPresent()) {
                result.put("code", 200);
                result.put("message", "该单词已在生词本中");
                return result;
            }
            
            MistakeWord mistake = new MistakeWord(userId, wordId);
            mistakeWordRepository.save(mistake);
            
            result.put("code", 200);
            result.put("message", "已添加到生词本");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 从生词本移除单词
     */
    @PostMapping("/remove")
    public Map<String, Object> remove(@RequestBody Map<String, String> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            String userId = payload.get("userId");
            String wordId = payload.get("wordId");
            
            if (userId == null || wordId == null) {
                result.put("code", 400);
                result.put("message", "参数缺失");
                return result;
            }
            
            mistakeWordRepository.deleteByUserIdAndWordId(userId, wordId);
            
            result.put("code", 200);
            result.put("message", "已从生词本移除");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 获取生词本复习列表（含单词详情和用户熟悉度）
     */
    @GetMapping("/review/list")
    public Map<String, Object> getReviewList(@RequestParam String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<MistakeWord> mistakes = mistakeWordRepository.findByUserIdOrderByCreateTimeDesc(userId);
            List<Map<String, Object>> data = new ArrayList<>();

            for (MistakeWord mistake : mistakes) {
                Map<String, Object> item = new HashMap<>();
                item.put("recordId", mistake.getRecordId());
                item.put("wordId", mistake.getWordId());

                wordRepository.findById(mistake.getWordId()).ifPresent(word -> {
                    item.put("content", word.getContent());
                    item.put("translation", word.getTranslation());
                    item.put("phonetic", word.getPhonetic());
                    item.put("partOfSpeech", word.getPartOfSpeech());
                });

                // 获取用户对该词的熟悉度
                int familiarity = familiarityRepository.findByUserIdAndWordId(userId, mistake.getWordId())
                        .map(UserWordFamiliarity::getFamiliarity)
                        .orElse(50);
                item.put("familiarity", familiarity);

                data.add(item);
            }

            result.put("code", 200);
            result.put("data", data);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 复习时标记单词为"已认识"：移出生词本，熟悉度设为 max(当前, 70)
     */
    @PostMapping("/review/known")
    public Map<String, Object> markKnown(@RequestBody Map<String, String> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            String userId = payload.get("userId");
            String wordId = payload.get("wordId");
            if (userId == null || wordId == null) {
                result.put("code", 400);
                result.put("message", "参数缺失");
                return result;
            }

            // 移出生词本
            mistakeWordRepository.deleteByUserIdAndWordId(userId, wordId);

            // 熟悉度设为 max(当前, 70)
            UserWordFamiliarity uf = familiarityRepository.findByUserIdAndWordId(userId, wordId)
                    .orElse(new UserWordFamiliarity(userId, wordId, 70, System.currentTimeMillis()));
            int newFam = Math.max(uf.getFamiliarity(), 70);
            uf.setFamiliarity(newFam);
            uf.setLastUpdate(System.currentTimeMillis());
            familiarityRepository.save(uf);

            result.put("code", 200);
            result.put("newFamiliarity", newFam);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 复习时标记单词为"不认识"：仅记录复习次数，留在生词本
     */
    @PostMapping("/review/unfamiliar")
    public Map<String, Object> markUnfamiliar(@RequestBody Map<String, String> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            String userId = payload.get("userId");
            String wordId = payload.get("wordId");
            if (userId == null || wordId == null) {
                result.put("code", 400);
                result.put("message", "参数缺失");
                return result;
            }

            // 增加复习次数
            mistakeWordRepository.findByUserIdAndWordId(userId, wordId).ifPresent(mw -> {
                mw.setReviewCount(mw.getReviewCount() + 1);
                mw.setLastReviewTime(System.currentTimeMillis());
                mistakeWordRepository.save(mw);
            });

            result.put("code", 200);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 统计用户生词数量
     */
    @GetMapping("/count")
    public Map<String, Object> getCount(@RequestParam String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            long count = mistakeWordRepository.countByUserId(userId);
            result.put("code", 200);
            result.put("count", count);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
