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
import org.springframework.transaction.annotation.Transactional;

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
                        String phonetic, WordType wordType, String userId) {
        if (wordType == WordType.CUSTOM) {
            // 自建词必须有所属用户
            if (userId == null || userId.isEmpty()) {
                throw new RuntimeException("自建词必须指定所属用户");
            }
            if (wordRepository.existsByContentAndUserIdAndWordType(content, userId, WordType.CUSTOM)) {
                throw new RuntimeException("您已添加过该单词");
            }
            Word newWord = new Word(content, partOfSpeech, translation, phonetic, wordType);
            newWord.setUserId(userId);
            return wordRepository.save(newWord);
        }
        // 考纲词无需 userId
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

    /**
     * 编辑单词后重新计算相似词义（基于中文释义汉字重叠率 ≥30%）
     */
    @Transactional
    public void recalculateSimilarMeanings(String wordId) {
        Word word = getWordById(wordId);
        String pos = word.getPartOfSpeech();
        String translation = word.getTranslation();
        if (pos == null || translation == null || translation.isEmpty()) return;

        // 取主词性
        String mainPos = pos.contains("/") ? pos.split("/")[0].trim() : pos;

        // 查找同词性的所有单词
        List<Word> samePosWords = wordRepository.findAll().stream()
                .filter(w -> w.getWordId() != null && !w.getWordId().equals(wordId))
                .filter(w -> w.getPartOfSpeech() != null && !w.getPartOfSpeech().isEmpty())
                .filter(w -> {
                    String wp = w.getPartOfSpeech().contains("/")
                            ? w.getPartOfSpeech().split("/")[0].trim()
                            : w.getPartOfSpeech();
                    return wp.equals(mainPos);
                })
                .collect(Collectors.toList());

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> similarList = new java.util.ArrayList<>();

        for (Word other : samePosWords) {
            String t2 = other.getTranslation();
            if (t2 == null || t2.isEmpty()) continue;

            double overlap = translationOverlap(translation, t2);
            if (overlap >= 0.3) {
                Map<String, Object> entry = new java.util.HashMap<>();
                entry.put("word_id", other.getWordId());
                entry.put("similarity_score", overlap);
                similarList.add(entry);
            }
        }

        similarList.sort((a, b) -> Double.compare(
                (double) b.get("similarity_score"),
                (double) a.get("similarity_score")
        ));

        // 保留最多 8 个
        if (similarList.size() > 8) similarList = similarList.subList(0, 8);

        try {
            word.setSimilarMeanings(mapper.writeValueAsString(similarList));
            wordRepository.save(word);
        } catch (Exception ignored) {}
    }

    /**
     * 计算两个中文释义的汉字重叠率
     */
    private double translationOverlap(String t1, String t2) {
        if (t1 == null || t2 == null || t1.isEmpty() || t2.isEmpty()) return 0;
        String clean1 = t1.replaceAll("[\\s\\p{Punct}\\p{IsPunctuation}]", "");
        String clean2 = t2.replaceAll("[\\s\\p{Punct}\\p{IsPunctuation}]", "");
        java.util.Set<Character> chars1 = clean1.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());
        java.util.Set<Character> chars2 = clean2.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());
        java.util.Set<Character> intersection = new java.util.HashSet<>(chars1);
        intersection.retainAll(chars2);
        java.util.Set<Character> union = new java.util.HashSet<>(chars1);
        union.addAll(chars2);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    public List<Word> getAllWords() {
        return wordRepository.findAll();
    }

    public List<Word> getAllWordsOrderByContent() {
        return wordRepository.findAllOrderByContentAsc();
    }

    /**
     * 获取所有单词（按字典序），如果提供了 userId 则包含熟悉度
     * 自建词汇只返回当前用户创建的
     */
    public List<java.util.Map<String, Object>> getAllWordsWithFamiliarity(String userId) {
        List<Word> words = wordRepository.findAllOrderByContentAsc().stream()
                .filter(w -> w.getWordType() != WordType.CUSTOM || userId == null || userId.equals(w.getUserId()))
                .collect(Collectors.toList());
        return attachFamiliarityToWords(words, userId);
    }

    public List<Word> getWordsByType(WordType wordType) {
        return wordRepository.findByWordType(wordType);
    }

    public List<Word> getWordsByPhase(String phase) {
        return wordRepository.findAll().stream()
                .filter(w -> phase == null || phase.equals(w.getPhase()))
                .collect(Collectors.toList());
    }

    public List<Word> searchWords(String keyword) {
        return wordRepository.searchByKeyword(keyword);
    }

    /**
     * 搜索单词（含熟悉度）
     */
    public List<java.util.Map<String, Object>> searchWordsWithFamiliarity(String keyword, String userId) {
        List<Word> words = wordRepository.searchByKeyword(keyword).stream()
                .filter(w -> w.getWordType() != WordType.CUSTOM || userId == null || userId.equals(w.getUserId()))
                .collect(Collectors.toList());
        return attachFamiliarityToWords(words, userId);
    }

    /**
     * 给单词列表附加熟悉度（批量加载避免 N+1）
     */
    private List<java.util.Map<String, Object>> attachFamiliarityToWords(List<Word> words, String userId) {
        if (userId == null) {
            // 不传 userId 则返回原始 Word 列表（不含熟悉度）
            return words.stream().map(w -> {
                java.util.Map<String, Object> item = new java.util.HashMap<>();
                item.put("wordId", w.getWordId());
                item.put("content", w.getContent());
                item.put("translation", w.getTranslation());
                item.put("partOfSpeech", w.getPartOfSpeech());
                item.put("phonetic", w.getPhonetic());
                item.put("wordType", w.getWordType());
                item.put("phase", w.getPhase());
                return item;
            }).collect(Collectors.toList());
        }

        // 加载该用户的所有熟悉度记录
        java.util.Map<String, Integer> familiarityMap = familiarityRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(
                        UserWordFamiliarity::getWordId,
                        UserWordFamiliarity::getFamiliarity,
                        (v1, v2) -> v1
                ));

        // 加载所有最后交互时间
        java.util.Map<String, Long> lastInteractionMap = interactionRepository.findLastTimestampByUser(userId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) ((Object[]) row)[0],
                        row -> (Long) ((Object[]) row)[1]
                ));

        return words.stream().map(word -> {
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
        }).collect(Collectors.toList());
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

    public Map<String, Object> getWordStatistics(String userId) {
        List<Word> allWords = wordRepository.findAll();

        long syllabusCount = allWords.stream()
                .filter(w -> w.getWordType() == WordType.SYLLABUS)
                .count();

        long customCount = allWords.stream()
                .filter(w -> w.getWordType() == WordType.CUSTOM
                        && (userId == null || userId.equals(w.getUserId())))
                .count();

        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", syllabusCount + customCount);
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
                        if (!userId.equals(word.getUserId())) return false;
                    } else {
                        if (word.getWordType() == WordType.SYLLABUS && userPhase != null) {
                            if (!isInUserPhase(word.getPhase(), userPhase)) return false;
                        } else if (word.getWordType() == WordType.CUSTOM) {
                            if (!userId.equals(word.getUserId())) return false;
                        } else if (word.getWordType() != WordType.SYLLABUS) {
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
            case "non-student": return false;
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

