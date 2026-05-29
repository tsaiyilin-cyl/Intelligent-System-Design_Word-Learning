package cn.edu.cuc.class10.service;

import cn.edu.cuc.class10.entity.MistakeWord;
import cn.edu.cuc.class10.entity.Word;
import cn.edu.cuc.class10.entity.UserWordFamiliarity;
import cn.edu.cuc.class10.entity.WordType;
import cn.edu.cuc.class10.repository.InteractionRepository;
import cn.edu.cuc.class10.repository.MistakeWordRepository;
import cn.edu.cuc.class10.repository.UserWordFamiliarityRepository;
import cn.edu.cuc.class10.repository.WordRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WordService {

    @Autowired
    private WordRepository wordRepository;

    @Autowired
    private MistakeWordRepository mistakeWordRepository;

    @Autowired
    private UserWordFamiliarityRepository familiarityRepository;

    @Autowired
    private InteractionRepository interactionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Word addWord(String content, String partOfSpeech, String translation,
                        String phonetic, WordType wordType) {
        if (wordRepository.existsByContent(content)) {
            throw new RuntimeException("单词已存在");
        }

        Word newWord = new Word(content, partOfSpeech, translation, phonetic, wordType);
        return wordRepository.save(newWord);
    }

    public Word saveWord(Word word) {
        return wordRepository.save(word);
    }

    public void updateWordExtraFields(String wordId, String phrases, String sentences) {
        Optional<Word> wordOpt = wordRepository.findById(wordId);

        if (wordOpt.isEmpty()) {
            throw new RuntimeException("单词不存在");
        }

        Word word = wordOpt.get();

        if (phrases != null) {
            word.setPhrases(phrases);
        }

        if (sentences != null) {
            word.setSentences(sentences);
        }

        wordRepository.save(word);
    }

    public Word updateWord(String wordId, String content, String partOfSpeech,
                           String translation, String phonetic) {
        Optional<Word> wordOpt = wordRepository.findById(wordId);

        if (wordOpt.isEmpty()) {
            throw new RuntimeException("单词不存在");
        }

        Word word = wordOpt.get();

        if (content != null) {
            if (!content.equals(word.getContent()) && wordRepository.existsByContent(content)) {
                throw new RuntimeException("单词内容已存在");
            }
            word.setContent(content);
        }

        if (partOfSpeech != null) {
            word.setPartOfSpeech(partOfSpeech);
        }

        if (translation != null) {
            word.setTranslation(translation);
        }

        if (phonetic != null) {
            word.setPhonetic(phonetic);
        }

        return wordRepository.save(word);
    }

    public void deleteWord(String wordId) {
        if (!wordRepository.existsById(wordId)) {
            throw new RuntimeException("单词不存在");
        }
        wordRepository.deleteById(wordId);
    }

    public Word getWordById(String wordId) {
        return wordRepository.findById(wordId)
                .orElseThrow(() -> new RuntimeException("单词不存在"));
    }

    public List<Word> getAllWords() {
        return wordRepository.findAll();
    }

    public List<Word> getAllWordsOrderByContent() {
        return wordRepository.findAllOrderByContentAsc();
    }

    public List<Word> getWordsByType(WordType wordType) {
        return wordRepository.findByWordType(wordType);
    }

    public List<Word> getWordsByPhase(String phase) {
        return wordRepository.findByContentContainingIgnoreCase(phase);
    }

    public List<Word> searchWords(String keyword) {
        return wordRepository.searchByKeyword(keyword);
    }

    public void addSimilarMeaning(String wordId, String targetWordId, Double similarityScore) {
        Word word = getWordById(wordId);

        try {
            String similarMeanings = word.getSimilarMeanings();
            List<Map<String, Object>> similarities;

            if (similarMeanings == null || similarMeanings.isEmpty()) {
                similarities = new java.util.ArrayList<>();
            } else {
                similarities = objectMapper.readValue(
                        similarMeanings,
                        new TypeReference<List<Map<String, Object>>>() {}
                );
            }

            Map<String, Object> newSimilarity = new java.util.HashMap<>();
            newSimilarity.put("word_id", targetWordId);
            newSimilarity.put("similarity_score", similarityScore);
            similarities.add(newSimilarity);

            word.setSimilarMeanings(objectMapper.writeValueAsString(similarities));
            wordRepository.save(word);

        } catch (Exception e) {
            throw new RuntimeException("更新相似词义群失败: " + e.getMessage());
        }
    }

    public void addSimilarSpelling(String wordId, String targetWordId, Integer editDistance) {
        Word word = getWordById(wordId);

        try {
            String similarSpellings = word.getSimilarSpellings();
            List<Map<String, Object>> similarities;

            if (similarSpellings == null || similarSpellings.isEmpty()) {
                similarities = new java.util.ArrayList<>();
            } else {
                similarities = objectMapper.readValue(
                        similarSpellings,
                        new TypeReference<List<Map<String, Object>>>() {}
                );
            }

            Map<String, Object> newSimilarity = new java.util.HashMap<>();
            newSimilarity.put("word_id", targetWordId);
            newSimilarity.put("edit_distance", editDistance);
            similarities.add(newSimilarity);

            word.setSimilarSpellings(objectMapper.writeValueAsString(similarities));
            wordRepository.save(word);

        } catch (Exception e) {
            throw new RuntimeException("更新相似词样群失败: " + e.getMessage());
        }
    }

    public Map<String, Object> getWordStatistics() {
        List<Word> allWords = wordRepository.findAll();

        long syllabusCount = allWords.stream()
                .filter(w -> w.getWordType() == WordType.SYLLABUS)
                .count();

        long customCount = allWords.stream()
                .filter(w -> w.getWordType() == WordType.CUSTOM)
                .count();

        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", allWords.size());
        stats.put("syllabusCount", syllabusCount);
        stats.put("customCount", customCount);

        return stats;
    }

    /**
     * 获取用户词汇域中有效熟悉度 <= 60 的单词（基于 UserWordFamiliarity + 艾宾浩斯时间衰减）
     * 返回数据已包含该用户的熟悉度，避免 N+1 查询
     */
    public List<java.util.Map<String, Object>> getLowFamiliarityWords(String userId, String userPhase, String filterType) {
        List<Word> allWords = wordRepository.findAllOrderByContentAsc();

        // 加载该用户的所有熟悉度记录
        java.util.Map<String, Integer> familiarityMap = familiarityRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(
                        UserWordFamiliarity::getWordId,
                        UserWordFamiliarity::getFamiliarity,
                        (v1, v2) -> v1
                ));

        // 加载该用户所有单词的最后交互时间
        java.util.Map<String, Long> lastInteractionMap = interactionRepository.findLastTimestampByUser(userId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) ((Object[]) row)[0],
                        row -> (Long) ((Object[]) row)[1]
                ));

        return allWords.stream()
                .filter(word -> {
                    if ("SYLLABUS".equals(filterType)) {
                        if (word.getWordType() != WordType.SYLLABUS) return false;
                        if (userPhase != null && !isInUserPhase(word.getPhase(), userPhase)) return false;
                    } else if ("CUSTOM".equals(filterType)) {
                        if (word.getWordType() != WordType.CUSTOM) return false;
                    } else {
                        if (word.getWordType() == WordType.SYLLABUS && userPhase != null) {
                            if (!isInUserPhase(word.getPhase(), userPhase)) return false;
                        } else if (word.getWordType() != WordType.CUSTOM && word.getWordType() != WordType.SYLLABUS) {
                            return false;
                        }
                    }
                    int stored = familiarityMap.getOrDefault(word.getWordId(), 50);
                    Long lastTime = lastInteractionMap.get(word.getWordId());
                    return applyDecay(stored, lastTime) <= 60;
                })
                .map(word -> {
                    java.util.Map<String, Object> item = new java.util.HashMap<>();
                    item.put("wordId", word.getWordId());
                    item.put("content", word.getContent());
                    item.put("translation", word.getTranslation());
                    item.put("partOfSpeech", word.getPartOfSpeech());
                    item.put("phonetic", word.getPhonetic());
                    item.put("wordType", word.getWordType());
                    item.put("phase", word.getPhase());
                    item.put("phrases", word.getPhrases());
                    item.put("sentences", word.getSentences());
                    item.put("similarMeanings", word.getSimilarMeanings());
                    item.put("similarSpellings", word.getSimilarSpellings());
                    int stored = familiarityMap.getOrDefault(word.getWordId(), 50);
                    Long lastTime = lastInteractionMap.get(word.getWordId());
                    item.put("familiarity", applyDecay(stored, lastTime));
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * 判断单词阶段是否在用户阶段范围内
     */
    private boolean isInUserPhase(String wordPhase, String userPhase) {
        if (wordPhase == null) return true;
        switch (userPhase) {
            case "primary": return "primary".equals(wordPhase);
            case "junior": return "primary".equals(wordPhase) || "junior".equals(wordPhase);
            case "senior": return true;
            default: return true;
        }
    }

    /**
     * 更新用户对单词的熟悉度（写入 user_word_familiarity 表，按用户隔离）
     */
    public void updateWordFamiliarity(String wordId, int newFamiliarity, String userId) {
        if (userId == null) return;
        UserWordFamiliarity uf = familiarityRepository.findByUserIdAndWordId(userId, wordId)
                .orElse(new UserWordFamiliarity(userId, wordId, newFamiliarity, System.currentTimeMillis()));
        uf.setFamiliarity(newFamiliarity);
        uf.setLastUpdate(System.currentTimeMillis());
        familiarityRepository.save(uf);
    }

    /**
     * 获取用户对单词的存储熟悉度（原始值，0-230，默认 50）
     */
    public int getUserFamiliarity(String userId, String wordId) {
        return familiarityRepository.findByUserIdAndWordId(userId, wordId)
                .map(UserWordFamiliarity::getFamiliarity)
                .orElse(50);
    }

    /**
     * 获取经过时间衰减后的熟悉度基准值（用于更新前读取，基于熟悉度表自身的 lastUpdate）
     * 确保后续的增减操作在已衰减的基础上进行，使衰减持久化到数据库
     */
    public int getDecayedBaselineFamiliarity(String userId, String wordId) {
        return familiarityRepository.findByUserIdAndWordId(userId, wordId)
                .map(uf -> applyDecay(uf.getFamiliarity(), uf.getLastUpdate()))
                .orElse(50);
    }

    /**
     * 根据艾宾浩斯遗忘曲线计算有效熟悉度
     * ≥2天无交互 ×0.33，≥4天再×0.83，≥7天再×0.92
     */
    public int getEffectiveFamiliarity(String userId, String wordId) {
        int stored = getUserFamiliarity(userId, wordId);
        Long lastTime = interactionRepository.findLastTimestampByUserAndWord(userId, wordId).orElse(null);
        return applyDecay(stored, lastTime);
    }

    /**
     * 批量获取有效熟悉度（用于 getLowFamiliarityWords）
     */
    public int applyDecay(int storedFamiliarity, Long lastInteractionTime) {
        if (lastInteractionTime == null || storedFamiliarity <= 0) return storedFamiliarity;
        long days = (System.currentTimeMillis() - lastInteractionTime) / 86400000L;
        if (days >= 7) {
            return (int)(storedFamiliarity * 0.33 * 0.83 * 0.92);
        } else if (days >= 4) {
            return (int)(storedFamiliarity * 0.33 * 0.83);
        } else if (days >= 2) {
            return (int)(storedFamiliarity * 0.33);
        }
        return storedFamiliarity;
    }

    /**
     * 添加单词到生词本
     */
    public void addToMistakeBook(String userId, String wordId) {
        Optional<MistakeWord> existing = mistakeWordRepository.findByUserIdAndWordId(userId, wordId);
        if (existing.isEmpty()) {
            MistakeWord mistake = new MistakeWord(userId, wordId);
            mistakeWordRepository.save(mistake);
        }
    }
}

