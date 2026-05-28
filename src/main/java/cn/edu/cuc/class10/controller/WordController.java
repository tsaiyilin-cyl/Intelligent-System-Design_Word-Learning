package cn.edu.cuc.class10.controller;

import cn.edu.cuc.class10.entity.Word;
import cn.edu.cuc.class10.entity.WordType;
import cn.edu.cuc.class10.service.WordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/words")
public class WordController {

    @Autowired
    private WordService wordService;

    @GetMapping("/add")
    public Map<String, Object> addWord(
            @RequestParam String content,
            @RequestParam String partOfSpeech,
            @RequestParam String translation,
            @RequestParam(required = false) String phonetic,
            @RequestParam String wordType,
            @RequestParam(required = false) String phrases,
            @RequestParam(required = false) String sentences) {

        Map<String, Object> result = new HashMap<>();

        try {
            WordType type = WordType.valueOf(wordType);

            Word word = wordService.addWord(content, partOfSpeech, translation, phonetic, type);
            
            // 如果是自建词汇，设置额外的可选字段
            if (type == WordType.CUSTOM) {
                if (phrases != null && !phrases.isEmpty()) {
                    word.setPhrases(phrases);
                }
                if (sentences != null && !sentences.isEmpty()) {
                    word.setSentences(sentences);
                }
                wordService.saveWord(word);
            }

            result.put("code", 200);
            result.put("message", "添加成功");
            result.put("data", word);
        } catch (IllegalArgumentException e) {
            result.put("code", 400);
            result.put("message", "参数错误：" + e.getMessage());
        } catch (Exception e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        }

        return result;
    }

    @GetMapping("/update")
    public Map<String, Object> updateWord(
            @RequestParam String wordId,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String partOfSpeech,
            @RequestParam(required = false) String translation,
            @RequestParam(required = false) String phonetic) {

        Map<String, Object> result = new HashMap<>();

        try {
            Word word = wordService.updateWord(wordId, content, partOfSpeech, translation, phonetic);

            result.put("code", 200);
            result.put("message", "更新成功");
            result.put("data", word);
        } catch (IllegalArgumentException e) {
            result.put("code", 400);
            result.put("message", "参数错误：" + e.getMessage());
        } catch (Exception e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        }

        return result;
    }

    @GetMapping("/updateExtra")
    public Map<String, Object> updateWordExtra(
            @RequestParam String wordId,
            @RequestParam(required = false) String phrases,
            @RequestParam(required = false) String sentences) {

        Map<String, Object> result = new HashMap<>();

        try {
            wordService.updateWordExtraFields(wordId, phrases, sentences);

            result.put("code", 200);
            result.put("message", "更新成功");
        } catch (Exception e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        }

        return result;
    }

    @GetMapping("/delete")
    public Map<String, Object> deleteWord(@RequestParam String wordId) {
        Map<String, Object> result = new HashMap<>();

        try {
            wordService.deleteWord(wordId);
            result.put("code", 200);
            result.put("message", "删除成功");
        } catch (Exception e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        }

        return result;
    }

    @GetMapping("/get")
    public Map<String, Object> getWord(@RequestParam String wordId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Word word = wordService.getWordById(wordId);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", word);
        } catch (Exception e) {
            result.put("code", 404);
            result.put("message", e.getMessage());
        }

        return result;
    }

    @GetMapping("/all")
    public Map<String, Object> getAllWords() {
        Map<String, Object> result = new HashMap<>();

        List<Word> words = wordService.getAllWords();
        result.put("code", 200);
        result.put("message", "查询成功");
        result.put("data", words);

        return result;
    }

    @GetMapping("/all/ordered")
    public Map<String, Object> getAllWordsOrdered() {
        Map<String, Object> result = new HashMap<>();

        List<Word> words = wordService.getAllWordsOrderByContent();
        result.put("code", 200);
        result.put("message", "查询成功（按字典序）");
        result.put("data", words);

        return result;
    }

    @GetMapping("/byType")
    public Map<String, Object> getWordsByType(@RequestParam String wordType) {
        Map<String, Object> result = new HashMap<>();

        try {
            WordType type = WordType.valueOf(wordType);
            List<Word> words = wordService.getWordsByType(type);

            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", words);
        } catch (IllegalArgumentException e) {
            result.put("code", 400);
            result.put("message", "参数错误：" + e.getMessage());
        } catch (Exception e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        }

        return result;
    }

    @GetMapping("/search")
    public Map<String, Object> searchWords(@RequestParam String keyword) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<Word> words = wordService.searchWords(keyword);

            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", words);
        } catch (Exception e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        }

        return result;
    }

    @GetMapping("/familiarity")
    public Map<String, Object> updateFamiliarity(
            @RequestParam String wordId,
            @RequestParam Integer familiarity) {
        Map<String, Object> result = new HashMap<>();

        try {
            wordService.updateFamiliarity(wordId, familiarity);
            result.put("code", 200);
            result.put("message", "更新成功");
        } catch (Exception e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        }

        return result;
    }

    @GetMapping("/statistics")
    public Map<String, Object> getStatistics() {
        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, Object> stats = wordService.getWordStatistics();
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", stats);
        } catch (Exception e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * 获取低熟悉度单词（用于学习模式）
     */
    @GetMapping("/study/lowFamiliarity")
    public Map<String, Object> getLowFamiliarityWords(
            @RequestParam String userId,
            @RequestParam String userPhase,
            @RequestParam(required = false) String filterType) {
        Map<String, Object> result = new HashMap<>();
        try {
            // filterType: all, SYLLABUS, CUSTOM
            if (filterType == null || filterType.isEmpty()) {
                filterType = "all";
            }
            List<Word> words = wordService.getLowFamiliarityWords(userPhase, filterType);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", words);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 更新单词熟悉度（学习反馈）
     */
    @PostMapping("/study/updateFamiliarity")
    public Map<String, Object> updateStudyFamiliarity(@RequestBody Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            String wordId = (String) payload.get("wordId");
            String action = (String) payload.get("action"); // familiar, vague, unfamiliar, mastered
            String userId = (String) payload.get("userId");

            if (wordId == null || action == null) {
                result.put("code", 400);
                result.put("message", "参数缺失");
                return result;
            }

            int newFamiliarity = 50; // 默认值
            boolean addToMistakeBook = false;

            switch (action) {
                case "familiar":
                    newFamiliarity = 90;
                    break;
                case "vague":
                    // 获取当前熟悉度，改为80%
                    Word currentWord = wordService.getWordById(wordId);
                    int currentFam = currentWord.getFamiliarity() != null ? currentWord.getFamiliarity() : 50;
                    newFamiliarity = (int) (currentFam * 0.8);
                    break;
                case "unfamiliar":
                    newFamiliarity = 30;
                    addToMistakeBook = true;
                    break;
                case "mastered":
                    newFamiliarity = 100;
                    break;
                default:
                    result.put("code", 400);
                    result.put("message", "无效的操作类型");
                    return result;
            }

            // 更新熟悉度
            wordService.updateWordFamiliarity(wordId, newFamiliarity);

            // 如果需要，添加到生词本
            if (addToMistakeBook && userId != null) {
                wordService.addToMistakeBook(userId, wordId);
            }

            result.put("code", 200);
            result.put("message", "更新成功");
            result.put("newFamiliarity", newFamiliarity);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }
}

