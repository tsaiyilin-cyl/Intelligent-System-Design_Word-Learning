package cn.edu.cuc.class10.controller;

import cn.edu.cuc.class10.entity.Word;
import cn.edu.cuc.class10.service.WordImageService;
import cn.edu.cuc.class10.service.WordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单词图片接口 —— AI 生成/图库搜索 + 本地缓存
 */
@RestController
@RequestMapping("/api/words/image")
public class WordImageController {

    private static final Logger log = LoggerFactory.getLogger(WordImageController.class);

    @Autowired
    private WordImageService wordImageService;

    @Autowired
    private WordService wordService;

    /**
     * 获取单词图片 URL（懒加载：不存在则自动生成）
     */
    @GetMapping("/{wordId}")
    public Map<String, Object> getWordImage(@PathVariable String wordId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Word word = wordService.getWordById(wordId);
            String imageUrl = wordImageService.getOrCreateImage(
                    wordId, word.getContent(), word.getTranslation());

            result.put("code", 200);
            result.put("data", Map.of(
                    "wordId", wordId,
                    "imageUrl", imageUrl
            ));
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "获取图片失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 检查单词是否有本地缓存的图片（不需要生成）
     */
    @GetMapping("/{wordId}/exists")
    public Map<String, Object> checkImageExists(@PathVariable String wordId) {
        Map<String, Object> result = new HashMap<>();
        boolean exists = wordImageService.hasLocalImage(wordId);
        result.put("code", 200);
        result.put("data", Map.of(
                "wordId", wordId,
                "exists", exists
        ));
        return result;
    }

    /**
     * 按单词内容搜索：查询该单词所有匹配记录的图片缓存状态
     * 用于后台管理 / 调试时确认图片是否已生成
     */
    @GetMapping("/search")
    public Map<String, Object> searchImages(@RequestParam String keyword) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Word> words = wordService.searchWords(keyword);
            List<Map<String, Object>> items = new ArrayList<>();
            for (Word w : words) {
                Map<String, Object> item = new HashMap<>();
                item.put("wordId", w.getWordId());
                item.put("content", w.getContent());
                item.put("translation", w.getTranslation());
                item.put("wordType", w.getWordType());
                item.put("hasImage", wordImageService.hasLocalImage(w.getWordId()));
                item.put("imageUrl", wordImageService.hasLocalImage(w.getWordId())
                        ? "/word-images/" + w.getWordId().replaceAll("[^a-zA-Z0-9_-]", "_") + ".jpg"
                        : null);
                items.add(item);
            }
            result.put("code", 200);
            result.put("data", items);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "搜索失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 按单词内容手动触发图片生成（异步后台处理）
     * 用于后台管理：不等待生成完成，提交后立即返回
     */
    @PostMapping("/generate")
    public Map<String, Object> generateImage(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String wordId = body.get("wordId");
            if (wordId == null || wordId.isEmpty()) {
                result.put("code", 400);
                result.put("message", "缺少 wordId");
                return result;
            }

            Word w = wordService.getWordById(wordId);
            wordImageService.preloadAsync(wordId, w.getContent(), w.getTranslation());

            result.put("code", 200);
            result.put("message", "已提交生成任务");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "提交失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 批量预加载单词图片（异步后台处理，不阻塞调用方）
     * 适用于：加载单词列表、测试题目、学习单词时提前触发
     */
    @PostMapping("/preload")
    public Map<String, Object> preloadImages(@RequestBody Map<String, List<String>> body) {
        Map<String, Object> result = new HashMap<>();
        List<String> wordIds = body.get("wordIds");
        if (wordIds == null || wordIds.isEmpty()) {
            result.put("code", 200);
            result.put("message", "没有需要预加载的单词");
            return result;
        }

        List<WordImageService.PreloadTask> tasks = new ArrayList<>();
        for (String wordId : wordIds) {
            try {
                Word w = wordService.getWordById(wordId);
                if (!wordImageService.hasLocalImage(wordId)) {
                    tasks.add(new WordImageService.PreloadTask(wordId, w.getContent(), w.getTranslation()));
                }
            } catch (Exception e) {
                log.warn("预加载跳过单词 [{}]: {}", wordId, e.getMessage());
            }
        }

        wordImageService.preloadBatchAsync(tasks);

        result.put("code", 200);
        result.put("message", "已提交 " + tasks.size() + " 个图片预加载任务");
        return result;
    }
}
