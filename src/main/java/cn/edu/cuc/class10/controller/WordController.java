package cn.edu.cuc.class10.controller;

import cn.edu.cuc.class10.entity.Interaction;
import cn.edu.cuc.class10.entity.Word;
import cn.edu.cuc.class10.entity.WordType;
import cn.edu.cuc.class10.repository.InteractionRepository;
import cn.edu.cuc.class10.service.WordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/words")
public class WordController {

    private static final Logger logger = LoggerFactory.getLogger(WordController.class);

    @Autowired
    private WordService wordService;

    @Autowired
    private InteractionRepository interactionRepository;

    @GetMapping("/add")
    public Map<String, Object> addWord(
            @RequestParam String content,
            @RequestParam String partOfSpeech,
            @RequestParam String translation,
            @RequestParam(required = false) String phonetic,
            @RequestParam String wordType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String phrases,
            @RequestParam(required = false) String sentences) {

        Map<String, Object> result = new HashMap<>();

        try {
            WordType type = WordType.valueOf(wordType);

            Word word = wordService.addWord(content, partOfSpeech, translation, phonetic, type, userId);
            
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

            // 释义变化时重新计算相似词义
            if (translation != null) {
                wordService.recalculateSimilarMeanings(wordId);
            }

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
    public Map<String, Object> getWord(
            @RequestParam String wordId,
            @RequestParam(required = false) String userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Word word = wordService.getWordById(wordId);
            // 构建返回数据，包含用户隔离的熟悉度
            Map<String, Object> data = new HashMap<>();
            data.put("wordId", word.getWordId());
            data.put("content", word.getContent());
            data.put("translation", word.getTranslation());
            data.put("partOfSpeech", word.getPartOfSpeech());
            data.put("phonetic", word.getPhonetic());
            data.put("wordType", word.getWordType());
            data.put("phase", word.getPhase());
            data.put("phrases", word.getPhrases());
            data.put("sentences", word.getSentences());
            data.put("similarMeanings", word.getSimilarMeanings());
            data.put("similarSpellings", word.getSimilarSpellings());
            data.put("familiarity",
                    userId != null ? wordService.getEffectiveFamiliarity(userId, wordId) : 50);

            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", data);
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
    public Map<String, Object> getAllWordsOrdered(
            @RequestParam(required = false) String userId) {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> data = wordService.getAllWordsWithFamiliarity(userId);
        result.put("code", 200);
        result.put("message", "查询成功（按字典序）");
        result.put("data", data);

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
    public Map<String, Object> searchWords(
            @RequestParam String keyword,
            @RequestParam(required = false) String userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<Map<String, Object>> data = wordService.searchWordsWithFamiliarity(keyword, userId);

            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", data);
        } catch (Exception e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * 在线查询单词的中文释义（调用有道词典）
     * 用于 OCR 照片识词时自动补充翻译
     * GET /api/words/translate?word=frog
     */
    @GetMapping("/translate")
    public Map<String, Object> translateWord(@RequestParam String word) {
        Map<String, Object> result = new HashMap<>();
        if (word == null || word.trim().isEmpty()) {
            result.put("code", 400);
            result.put("message", "单词不能为空");
            return result;
        }

        try {
            String cleanWord = word.trim().toLowerCase().replaceAll("\\s+", " ");

            // 调用有道词典 suggest API（免费，无需 Key）
            String urlStr = "https://dict.youdao.com/suggest?q="
                    + java.net.URLEncoder.encode(cleanWord, "UTF-8")
                    + "&num=1&doctype=json&vendor=web";

            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                result.put("code", 502);
                result.put("message", "在线词典服务暂不可用");
                return result;
            }

            String jsonResponse = new String(
                    conn.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);

            // 解析 JSON 提取释义
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonResponse);

            String translation = "";
            String phonetic = "";
            String partOfSpeech = "";

            com.fasterxml.jackson.databind.JsonNode entries = root.path("data").path("entries");
            if (entries.isArray() && entries.size() > 0) {
                String explain = entries.get(0).path("explain").asText("");
                String entry = entries.get(0).path("entry").asText("");

                // 解析释义，格式如 "n. 蛙，青蛙；... " → 提取中文部分
                if (!explain.isEmpty()) {
                    // 提取词性（如 "n."、"v."、"adj." 等）
                    java.util.regex.Matcher posMatcher =
                            java.util.regex.Pattern.compile("^([a-zA-Z]+\\.\\s*)").matcher(explain);
                    if (posMatcher.find()) {
                        partOfSpeech = posMatcher.group(1).replace(".", "").trim().toUpperCase();
                        // 如果词性映射表里有对应值则映射
                        partOfSpeech = mapPosAbbreviation(partOfSpeech);
                    }

                    // 提取中文（去掉词性前缀，取第一个分号/句号前的内容作为简短释义）
                    String chinesePart = explain.replaceAll("^[a-zA-Z]+\\.\\s*", "");
                    // 取第一个句号或分号前的内容
                    int dotIdx = chinesePart.indexOf("。");
                    int semiIdx = chinesePart.indexOf("；");
                    int commaIdx = chinesePart.indexOf("，");
                    int endIdx = -1;
                    if (dotIdx > 0) endIdx = dotIdx;
                    if (semiIdx > 0 && (endIdx < 0 || semiIdx < endIdx)) endIdx = semiIdx;
                    if (commaIdx > 0 && (endIdx < 0 || commaIdx < endIdx)) endIdx = commaIdx;
                    if (endIdx > 0) {
                        translation = chinesePart.substring(0, endIdx);
                    } else {
                        translation = chinesePart;
                    }
                    translation = translation.trim();
                }
            }

            if (translation.isEmpty()) {
                result.put("code", 404);
                result.put("message", "未找到该单词的释义");
                return result;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("word", cleanWord);
            data.put("translation", translation);
            data.put("phonetic", phonetic);
            data.put("partOfSpeech", partOfSpeech.isEmpty() ? null : partOfSpeech);

            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", data);

        } catch (java.net.SocketTimeoutException e) {
            result.put("code", 504);
            result.put("message", "在线词典查询超时，请检查网络");
        } catch (Exception e) {
            logger.warn("Online translate failed for '{}': {}", word, e.getMessage());
            result.put("code", 502);
            result.put("message", "在线词典服务暂不可用");
        }
        return result;
    }

    /** 词性缩写映射 */
    private String mapPosAbbreviation(String abbr) {
        if (abbr == null) return null;
        return switch (abbr.toUpperCase()) {
            case "N" -> "NOUN";
            case "V" -> "VERB";
            case "ADJ" -> "ADJECTIVE";
            case "ADV" -> "ADVERB";
            case "PREP" -> "PREPOSITION";
            case "CONJ" -> "CONJUNCTION";
            case "PRON" -> "PRONOUN";
            case "INT" -> "INTERJECTION";
            case "ART" -> "ARTICLE";
            case "AUX" -> "AUXILIARY";
            case "NUM" -> "NUMERAL";
            case "DET" -> "DETERMINER";
            default -> abbr;
        };
    }

    @GetMapping("/statistics")
    public Map<String, Object> getStatistics(
            @RequestParam(required = false) String userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, Object> stats = wordService.getWordStatistics(userId);
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
            List<Map<String, Object>> wordData = wordService.getLowFamiliarityWords(userId, userPhase, filterType);
            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", wordData);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 更新单词熟悉度（学习反馈）
     */
    @Transactional(rollbackFor = Exception.class)
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

            int currentFam = wordService.getDecayedBaselineFamiliarity(userId, wordId);
            int newFamiliarity = currentFam;
            boolean addToMistakeBook = false;
            String interactionFeedback = action;

            switch (action) {
                case "familiar":
                    newFamiliarity = Math.min(currentFam * 2, 230);
                    break;
                case "vague":
                    newFamiliarity = (int) (currentFam * 0.58);
                    break;
                case "unfamiliar":
                    newFamiliarity = (int) (currentFam * 0.33);
                    addToMistakeBook = true;
                    break;
                case "mastered":
                    newFamiliarity = 230;
                    break;
                default:
                    result.put("code", 400);
                    result.put("message", "无效的操作类型");
                    return result;
            }

            // 记录交互（用于时间衰减计算）
            if (userId != null) {
                interactionRepository.save(new Interaction(userId, wordId, "study_" + interactionFeedback, System.currentTimeMillis()));
            }

            // 更新熟悉度（写入 user_word_familiarity 表，按用户隔离）
            wordService.updateWordFamiliarity(wordId, newFamiliarity, userId);

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

