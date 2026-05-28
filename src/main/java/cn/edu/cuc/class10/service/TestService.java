package cn.edu.cuc.class10.service;

import cn.edu.cuc.class10.dto.TestQuestion;
import cn.edu.cuc.class10.entity.MistakeWord;
import cn.edu.cuc.class10.entity.User;
import cn.edu.cuc.class10.entity.Word;
import cn.edu.cuc.class10.entity.WordType;
import cn.edu.cuc.class10.entity.UserWordFamiliarity;
import cn.edu.cuc.class10.entity.TestSession;
import cn.edu.cuc.class10.entity.TestAnswerRecord;
import cn.edu.cuc.class10.repository.MistakeWordRepository;
import cn.edu.cuc.class10.repository.UserRepository;
import cn.edu.cuc.class10.repository.WordRepository;
import cn.edu.cuc.class10.repository.UserWordFamiliarityRepository;
import cn.edu.cuc.class10.repository.TestSessionRepository;
import cn.edu.cuc.class10.repository.TestAnswerRecordRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TestService {

    @Autowired
    private WordRepository wordRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserWordFamiliarityRepository familiarityRepository;
    @Autowired
    private InteractionService interactionService;
    @Autowired
    private TestSessionRepository testSessionRepository;
    @Autowired
    private TestAnswerRecordRepository answerRecordRepository;
    @Autowired
    private MistakeWordRepository mistakeWordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Random random = new Random();
    @Transactional
    public Map<String, Object> generateQuestions(String userId, int count, String type) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        List<Word> candidateWords = getCandidateWords(user);
        if (candidateWords.isEmpty()) {
            throw new RuntimeException("没有可学习的单词");
        }

        // 批量加载熟悉度
        Map<String, Integer> familiarityMap = familiarityRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(
                        UserWordFamiliarity::getWordId,
                        UserWordFamiliarity::getFamiliarity,
                        (v1, v2) -> v1
                ));

        // 计算权重
        List<WordWeight> wordWeights = candidateWords.stream()
                .map(word -> {
                    int familiarity = familiarityMap.getOrDefault(word.getWordId(), 50);
                    double weight = (100 - familiarity) / 100.0;
                    if ("memory".equals(user.getUserType()) && familiarity >= 40 && familiarity <= 70) {
                        weight *= 1.5;
                    }
                    return new WordWeight(word, weight);
                })
                .collect(Collectors.toList());

        List<TestQuestion> questions = new ArrayList<>();
        for (int i = 0; i < count && !wordWeights.isEmpty(); i++) {
            Word selected = weightedRandom(wordWeights);
            wordWeights.removeIf(ww -> ww.word.getWordId().equals(selected.getWordId()));
            String questionType = (type != null && !type.isEmpty()) ? mapQuestionType(type) : randomType();
            TestQuestion q = buildQuestion(selected, questionType);
            questions.add(q);
        }

        // 生成 sessionId 但不立即保存，等待测试完成后再保存
        String sessionId = UUID.randomUUID().toString();

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("questions", questions);
        return result;
    }

    private List<Word> getCandidateWords(User user) {
        List<Word> allWords = wordRepository.findAll();
        String userPhase = user.getPhase();
        return allWords.stream()
                .filter(w -> (w.getWordType() == WordType.SYLLABUS && isPhaseMatch(w.getPhase(), userPhase))
                        || w.getWordType() == WordType.CUSTOM)
                .collect(Collectors.toList());
    }

    private boolean isPhaseMatch(String wordPhase, String userPhase) {
        if (wordPhase == null) return true;
        switch (userPhase) {
            case "primary": return "primary".equals(wordPhase);
            case "junior": return "primary".equals(wordPhase) || "junior".equals(wordPhase);
            case "senior": return true;
            default: return true;
        }
    }

    private TestQuestion buildQuestion(Word word, String type) {
        TestQuestion q = new TestQuestion();
        q.setQuestionId(UUID.randomUUID().toString());
        q.setWordId(word.getWordId());
        q.setType(type);
        // 通用：设置词性和释义
        q.setPartOfSpeech(getChinesePos(word.getPartOfSpeech()));
        q.setTranslation(word.getTranslation());

        switch (type) {
            case "en2zh_choice":
                q.setContent(word.getContent());
                List<String> options = new ArrayList<>();
                options.add(word.getTranslation());
                // 从相似拼写群获取干扰项（形似词干扰）
                List<String> distractors = getDistractorsFromSimilarity(word, "en2zh_choice", 3);
                options.addAll(distractors);
                Collections.shuffle(options, random);
                q.setOptions(options);
                q.setCorrectAnswer(word.getTranslation());
                break;
            case "zh2en_choice":
                q.setContent(word.getTranslation());
                options = new ArrayList<>();
                options.add(word.getContent());
                // 从相似词义群获取干扰项（近义词干扰）
                distractors = getDistractorsFromSimilarity(word, "zh2en_choice", 3);
                options.addAll(distractors);
                Collections.shuffle(options, random);
                q.setOptions(options);
                q.setCorrectAnswer(word.getContent());
                break;
            case "spelling":
                // 拼写题：无需 content 和 options
                q.setCorrectAnswer(word.getContent());
                break;
            // 如果还有 listen_choice 可保留，但前端已移除
        }
        return q;
    }

    private String mapQuestionType(String frontendType) {
        switch (frontendType) {
            case "en2zh": return "en2zh_choice";
            case "zh2en": return "zh2en_choice";
            case "spelling": return "spelling";
            default: return frontendType;
        }
    }

    private String randomType() {
        String[] types = {"en2zh_choice", "zh2en_choice", "spelling"};
        return types[random.nextInt(types.length)];
    }

    private Word weightedRandom(List<WordWeight> list) {
        double totalWeight = list.stream().mapToDouble(ww -> ww.weight).sum();
        double rand = random.nextDouble() * totalWeight;
        double accum = 0;
        for (WordWeight ww : list) {
            accum += ww.weight;
            if (rand <= accum) return ww.word;
        }
        return list.get(0).word;
    }

    private static class WordWeight {
        Word word;
        double weight;
        WordWeight(Word word, double weight) { this.word = word; this.weight = weight; }
    }

    /**
     * 从相似词群中获取干扰选项
     * - en2zh_choice: 从相似拼写群（形似词）获取其释义作为干扰项
     * - zh2en_choice: 从相似词义群（近义词）获取其拼写作为干扰项
     */
    private List<String> getDistractorsFromSimilarity(Word word, String questionType, int count) {
        Set<String> distractors = new LinkedHashSet<>();

        try {
            String jsonField = "en2zh_choice".equals(questionType)
                    ? word.getSimilarSpellings()
                    : word.getSimilarMeanings();
            if (jsonField == null || jsonField.isEmpty()) return randomFallback(word, questionType, count);

            List<Map<String, Object>> similarEntries = objectMapper.readValue(
                    jsonField,
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            for (Map<String, Object> entry : similarEntries) {
                String similarWordId = (String) entry.get("word_id");
                wordRepository.findById(similarWordId).ifPresent(similarWord -> {
                    String option = "en2zh_choice".equals(questionType)
                            ? similarWord.getTranslation()
                            : similarWord.getContent();
                    if (option != null && !option.isEmpty()
                            && !option.equals(word.getTranslation())
                            && !option.equals(word.getContent())) {
                        distractors.add(option);
                    }
                });
                if (distractors.size() >= count) break;
            }
        } catch (Exception ignored) {
            // Fall through to random fallback
        }

        if (distractors.size() < count) {
            distractors.addAll(randomFallback(word, questionType, count - distractors.size()));
        }

        List<String> result = new ArrayList<>(distractors);
        Collections.shuffle(result, random);
        return result.size() > count ? result.subList(0, count) : result;
    }

    /**
     * 随机获取干扰项（兜底方案）
     */
    private List<String> randomFallback(Word word, String questionType, int count) {
        List<Word> others = wordRepository.findAll().stream()
                .filter(w -> !w.getWordId().equals(word.getWordId()))
                .collect(Collectors.toList());
        Collections.shuffle(others, random);

        return others.stream()
                .map(w -> "en2zh_choice".equals(questionType) ? w.getTranslation() : w.getContent())
                .filter(opt -> opt != null && !opt.isEmpty()
                        && !opt.equals(word.getTranslation())
                        && !opt.equals(word.getContent()))
                .distinct()
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * 将英文词性代码转换为中文显示名称
     */
    private String getChinesePos(String posCode) {
        if (posCode == null || posCode.isEmpty()) return "词组";
        Map<String, String> map = new HashMap<>();
        map.put("NOUN", "名词");
        map.put("VERB", "动词");
        map.put("ADJECTIVE", "形容词");
        map.put("ADVERB", "副词");
        map.put("PREPOSITION", "介词");
        map.put("CONJUNCTION", "连词");
        map.put("PRONOUN", "代词");
        map.put("INTERJECTION", "感叹词");
        map.put("ARTICLE", "冠词");
        map.put("NUMERAL", "数词");
        // 处理多词性（用/分隔）
        if (posCode.contains("/")) {
            String[] parts = posCode.split("/");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (sb.length() > 0) sb.append("/");
                sb.append(map.getOrDefault(p.trim(), p.trim()));
            }
            return sb.toString();
        }
        return map.getOrDefault(posCode, posCode);
    }

    public boolean submitAnswer(String userId, String wordId, String userAnswer, String correctAnswer) {
        boolean isCorrect = correctAnswer.equalsIgnoreCase(userAnswer.trim());
        String feedback = isCorrect ? "known" : "unknown";
        interactionService.recordInteraction(userId, wordId, feedback);
        
        // 如果答错，自动加入生词本（检查不重复）
        if (!isCorrect) {
            addToMistakeBookIfNotExists(userId, wordId);
        }
        
        return isCorrect;
    }

    /**
     * 将单词加入生词本（如果不存在）
     */
    private void addToMistakeBookIfNotExists(String userId, String wordId) {
        // 检查是否已存在
        Optional<MistakeWord> existing = mistakeWordRepository.findByUserIdAndWordId(userId, wordId);
        if (existing.isEmpty()) {
            MistakeWord mistake = new MistakeWord(userId, wordId);
            mistakeWordRepository.save(mistake);
        }
    }

    /**
     * 保存测试结果
     */
    public String saveTestResult(String userId, String questionType, int totalQuestions,
                               int correctCount, long startTime, long endTime, String sessionId) {
        // 如果前端传了 sessionId，就使用它；否则生成新的
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }
        
        TestSession session = new TestSession(userId, questionType, totalQuestions,
                correctCount, startTime, endTime);
        session.setSessionId(sessionId);  // 使用传入的 sessionId
        testSessionRepository.save(session);
        return session.getSessionId();
    }

    /**
     * 保存单题答题记录
     */
    public void saveAnswerRecord(String sessionId, int questionIndex, String wordId,
                                 String questionType, String questionContent,
                                 String correctAnswer, String userAnswer, boolean isCorrect,
                                 String optionsJson) {
        TestAnswerRecord record = new TestAnswerRecord(
                sessionId, questionIndex, wordId, questionType, questionContent,
                correctAnswer, userAnswer, isCorrect, optionsJson
        );
        answerRecordRepository.save(record);
    }

    /**
     * 获取测试会话的答题详情
     */
    public Map<String, Object> getTestDetail(String sessionId) {
        TestSession session = testSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("测试会话不存在"));
        List<TestAnswerRecord> records = answerRecordRepository.findBySessionIdOrderByQuestionIndexAsc(sessionId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("session", session);
        result.put("records", records);
        return result;
    }
}