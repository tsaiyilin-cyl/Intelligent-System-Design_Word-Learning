package cn.edu.cuc.class10.service;

import cn.edu.cuc.class10.entity.MistakeWord;
import cn.edu.cuc.class10.entity.Word;
import cn.edu.cuc.class10.entity.WordType;
import cn.edu.cuc.class10.repository.MistakeWordRepository;
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

    public void updateFamiliarity(String wordId, Integer familiarity) {
        Word word = getWordById(wordId);

        if (familiarity < 0 || familiarity > 100) {
            throw new RuntimeException("熟悉度必须在 0-100 之间");
        }

        word.setFamiliarity(familiarity);
        wordRepository.save(word);
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

        double avgFamiliarity = allWords.stream()
                .filter(w -> w.getFamiliarity() != null)
                .mapToInt(Word::getFamiliarity)
                .average()
                .orElse(0.0);

        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", allWords.size());
        stats.put("syllabusCount", syllabusCount);
        stats.put("customCount", customCount);
        stats.put("avgFamiliarity", Math.round(avgFamiliarity * 100.0) / 100.0);

        return stats;
    }

    /**
     * 获取用户词汇域中熟悉度 <= 70 的单词
     */
    public List<Word> getLowFamiliarityWords(String userPhase, String filterType) {
        List<Word> allWords = wordRepository.findAllOrderByContentAsc();
        
        return allWords.stream()
                .filter(word -> {
                    // 过滤词汇域
                    if ("SYLLABUS".equals(filterType)) {
                        if (word.getWordType() != WordType.SYLLABUS) return false;
                        if (userPhase != null && !isInUserPhase(word.getPhase(), userPhase)) return false;
                    } else if ("CUSTOM".equals(filterType)) {
                        if (word.getWordType() != WordType.CUSTOM) return false;
                    } else { // all
                        if (word.getWordType() == WordType.SYLLABUS && userPhase != null) {
                            if (!isInUserPhase(word.getPhase(), userPhase)) return false;
                        } else if (word.getWordType() != WordType.CUSTOM && word.getWordType() != WordType.SYLLABUS) {
                            return false;
                        }
                    }
                    // 过滤熟悉度
                    Integer familiarity = word.getFamiliarity();
                    return familiarity != null && familiarity <= 70;
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
     * 更新单词熟悉度
     */
    public void updateWordFamiliarity(String wordId, int newFamiliarity) {
        Optional<Word> wordOpt = wordRepository.findById(wordId);
        if (wordOpt.isEmpty()) {
            throw new RuntimeException("单词不存在");
        }
        Word word = wordOpt.get();
        word.setFamiliarity(newFamiliarity);
        wordRepository.save(word);
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

