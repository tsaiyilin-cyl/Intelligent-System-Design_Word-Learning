package cn.edu.cuc.class10.service;

import cn.edu.cuc.class10.dto.DashboardDataResponse;
import cn.edu.cuc.class10.entity.*;
import cn.edu.cuc.class10.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Calendar;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WordRepository wordRepository;
    @Autowired
    private UserWordFamiliarityRepository familiarityRepository;
    @Autowired
    private TestSessionRepository testSessionRepository;
    @Autowired
    private InteractionRepository interactionRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${study-tip.llm.base-url:}")
    private String llmBaseUrl;

    @Value("${study-tip.llm.api-key:}")
    private String llmApiKey;

    @Value("${study-tip.llm.model:deepseek-chat}")
    private String llmModel;

    @Value("${study-tip.llm.temperature:0.8}")
    private double llmTemperature;

    @Value("${study-tip.llm.max-tokens:120}")
    private int llmMaxTokens;

    private static final List<Map<String, String>> PREDEFINED_TIPS = List.of(
            Map.of("title", "制定合理目标", "content", "根据您的学习阶段和类型，建议每天学习10-20个新单词，循序渐进。"),
            Map.of("title", "定期复习", "content", "利用艾宾浩斯遗忘曲线，在适当的时间间隔复习已学单词，提高记忆效果。"),
            Map.of("title", "保持连续性", "content", "每天坚持学习，即使时间不长，也能形成良好的学习习惯，提升学习效果。"),
            Map.of("title", "多样化学习", "content", "结合单词卡片、测试练习等多种方式，全方位巩固单词记忆。")
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    public DashboardDataResponse getDashboardData(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 1. 获取用户可访问的所有单词（考纲阶段内+自建）
        List<Word> accessibleWords = getAccessibleWords(user);
        int totalWords = accessibleWords.size();

        // 2. 批量获取用户所有单词的熟悉度，放入 Map
        Map<String, Integer> familiarityMap = familiarityRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(
                        UserWordFamiliarity::getWordId,
                        UserWordFamiliarity::getFamiliarity,
                        (v1, v2) -> v1
                ));

        // 3. 获取最后交互时间（用于衰减计算）
        Map<String, Long> lastInteractionMap = interactionRepository.findLastTimestampByUser(userId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) ((Object[]) row)[0],
                        row -> (Long) ((Object[]) row)[1]
                ));

        // 4. 掌握单词数（衰减后有效熟悉度>=100）
        long masteredCount = accessibleWords.stream()
                .filter(w -> {
                    int stored = familiarityMap.getOrDefault(w.getWordId(), 50);
                    Long lastTime = lastInteractionMap.get(w.getWordId());
                    return applyDecay(stored, lastTime) >= 100;
                })
                .count();

        // 5. 近7天测试记录和正确率
        long now = System.currentTimeMillis();
        long sevenDaysAgo = now - 7 * 24 * 3600 * 1000L;
        List<TestSession> recentSessions = testSessionRepository.findByUserIdAndEndTimeBetween(userId, sevenDaysAgo, now);

        // 计算平均正确率
        double averageAccuracy = recentSessions.stream()
                .mapToDouble(s -> s.getCorrectCount() * 100.0 / s.getTotalQuestions())
                .average().orElse(0);

        // 每日正确率（按天聚合）
        Map<String, List<Double>> dailyAccuracies = new LinkedHashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (TestSession session : recentSessions) {
            String date = sdf.format(new Date(session.getEndTime()));
            double acc = session.getCorrectCount() * 100.0 / session.getTotalQuestions();
            dailyAccuracies.computeIfAbsent(date, k -> new ArrayList<>()).add(acc);
        }
        List<DashboardDataResponse.DailyAccuracy> dailyList = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : dailyAccuracies.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(v -> v).average().orElse(0);
            DashboardDataResponse.DailyAccuracy da = new DashboardDataResponse.DailyAccuracy();
            da.setDate(entry.getKey());
            da.setAccuracy(avg);
            dailyList.add(da);
        }

        // 6. 最近5次测试记录
        List<TestSession> latestFive = testSessionRepository.findByUserIdOrderByEndTimeDesc(userId);
        if (latestFive.size() > 5) latestFive = latestFive.subList(0, 5);
        List<DashboardDataResponse.TestRecord> testRecords = latestFive.stream().map(s -> {
            DashboardDataResponse.TestRecord tr = new DashboardDataResponse.TestRecord();
            tr.setSessionId(s.getSessionId());
            tr.setQuestionType(s.getQuestionType());
            tr.setTotal(s.getTotalQuestions());
            tr.setCorrect(s.getCorrectCount());
            tr.setAccuracy(s.getCorrectCount() * 100.0 / s.getTotalQuestions());
            tr.setEndTime(s.getEndTime());
            return tr;
        }).collect(Collectors.toList());

        // 7. 易错词TOP5：使用 SQL 聚合查询（仅查前5个）
        Pageable topFive = PageRequest.of(0, 5);
        List<Object[]> mistakeResults = interactionRepository.countMistakesByUserId(userId, topFive);
        List<DashboardDataResponse.MistakeWord> topMistakes = mistakeResults.stream()
                .map(row -> {
                    String wordId = (String) row[0];
                    Long count = (Long) row[1];
                    DashboardDataResponse.MistakeWord mw = new DashboardDataResponse.MistakeWord();
                    mw.setWordId(wordId);
                    mw.setMistakeCount(count.intValue());
                    // 获取单词内容
                    wordRepository.findById(wordId).ifPresent(w -> mw.setWordContent(w.getContent()));
                    return mw;
                })
                .collect(Collectors.toList());

        // 填充响应
        DashboardDataResponse response = new DashboardDataResponse();
        response.setTotalWords(totalWords);
        response.setMasteredWords((int) masteredCount);
        response.setAverageAccuracy(averageAccuracy);
        response.setRecentAccuracy(dailyList);
        response.setRecentTests(testRecords);
        response.setTopMistakes(topMistakes);
        return response;
    }

    /**
     * 获取单词列表（供报告详情查看）
     * @param filter all | mastered | unmastered | learned
     */
    public List<Map<String, Object>> getWordList(String userId, String filter) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        List<Word> accessibleWords = getAccessibleWords(user);

        Map<String, Integer> familiarityMap = familiarityRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(
                        UserWordFamiliarity::getWordId,
                        UserWordFamiliarity::getFamiliarity,
                        (v1, v2) -> v1
                ));

        Map<String, Long> lastInteractionMap = interactionRepository.findLastTimestampByUser(userId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) ((Object[]) row)[0],
                        row -> (Long) ((Object[]) row)[1]
                ));

        // 已学习单词：有交互记录或有熟悉度记录的单词
        Set<String> learnedIds = new HashSet<>();
        learnedIds.addAll(familiarityMap.keySet());
        learnedIds.addAll(lastInteractionMap.keySet());

        return accessibleWords.stream()
                .filter(w -> {
                    if ("learned".equals(filter)) {
                        return learnedIds.contains(w.getWordId());
                    }
                    int stored = familiarityMap.getOrDefault(w.getWordId(), 50);
                    Long lastTime = lastInteractionMap.get(w.getWordId());
                    int effective = applyDecay(stored, lastTime);
                    if ("mastered".equals(filter)) return effective >= 100;
                    if ("unmastered".equals(filter)) return effective < 100;
                    return true; // all
                })
                .map(w -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("wordId", w.getWordId());
                    item.put("content", w.getContent());
                    item.put("translation", w.getTranslation());
                    int stored = familiarityMap.getOrDefault(w.getWordId(), 50);
                    Long lastTime = lastInteractionMap.get(w.getWordId());
                    item.put("familiarity", applyDecay(stored, lastTime));
                    return item;
                })
                .sorted((a, b) -> ((String) a.get("content")).compareTo((String) b.get("content")))
                .collect(Collectors.toList());
    }

    /**
     * 实时获取学习计划数据
     */
    public Map<String, Object> getStudyPlanData(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        List<Word> accessibleWords = getAccessibleWords(user);
        Set<String> accessibleIds = accessibleWords.stream().map(Word::getWordId).collect(Collectors.toSet());

        // 熟悉度记录
        Map<String, Integer> familiarityMap = familiarityRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(
                        UserWordFamiliarity::getWordId,
                        UserWordFamiliarity::getFamiliarity,
                        (v1, v2) -> v1
                ));

        // 最后交互时间
        Map<String, Long> lastInteractionMap = interactionRepository.findLastTimestampByUser(userId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) ((Object[]) row)[0],
                        row -> (Long) ((Object[]) row)[1]
                ));

        // 已学习单词：有交互或熟悉度记录且在用户可访问范围内
        Set<String> learnedIds = new HashSet<>();
        learnedIds.addAll(familiarityMap.keySet());
        learnedIds.addAll(lastInteractionMap.keySet());
        learnedIds.retainAll(accessibleIds);
        int totalWords = learnedIds.size();

        // 已掌握单词：有效熟悉度 >= 100
        long masteredCount = accessibleWords.stream()
                .filter(w -> {
                    int stored = familiarityMap.getOrDefault(w.getWordId(), 50);
                    Long lastTime = lastInteractionMap.get(w.getWordId());
                    return applyDecay(stored, lastTime) >= 100;
                })
                .count();

        // 连续打卡天数
        List<Interaction> allInteractions = interactionRepository.findByUserId(userId);
        int studyStreak = calculateStreak(allInteractions);

        // 今日学习数
        long todayStart = getTodayStart();
        long todayLearned = allInteractions.stream()
                .filter(i -> i.getTimestamp() >= todayStart)
                .map(Interaction::getWordId)
                .distinct()
                .count();

        Map<String, Object> result = new HashMap<>();
        result.put("dailyGoal", user.getDailyGoal() != null ? user.getDailyGoal() : 10);
        result.put("totalWords", totalWords);
        result.put("masteredWords", (int) masteredCount);
        result.put("studyStreak", studyStreak);
        result.put("todayLearned", (int) todayLearned);
        return result;
    }

    /**
     * 获取学习建议（优先调用大模型，失败则使用预设建议）
     */
    public Map<String, Object> getStudyTip(String userId) {
        // 1. 获取用户数据
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 2. 获取学习统计数据
        Map<String, Object> planData = getStudyPlanData(userId);

        // 3. 尝试调用大模型 API
        if (llmApiKey != null && !llmApiKey.isBlank() && llmBaseUrl != null && !llmBaseUrl.isBlank()) {
            try {
                Map<String, String> llmTip = callLlmApi(user, planData);
                Map<String, Object> result = new HashMap<>();
                result.put("title", llmTip.get("title"));
                result.put("content", llmTip.get("content"));
                result.put("source", "ai");
                return result;
            } catch (Exception e) {
                log.warn("大模型建议生成失败，使用预设建议: {}", e.getMessage());
            }
        }

        // 4. 失败回退：随机选取一条预设建议
        Map<String, String> fallback = getRandomPredefinedTip();
        Map<String, Object> result = new HashMap<>();
        result.put("title", fallback.get("title"));
        result.put("content", fallback.get("content"));
        result.put("source", "predefined");
        return result;
    }

    /**
     * 调用大模型 API 生成个性化学习建议
     */
    private Map<String, String> callLlmApi(User user, Map<String, Object> planData) throws Exception {
        String phaseName = switch (user.getPhase()) {
            case "primary" -> "小学";
            case "junior" -> "初中";
            case "senior" -> "高中";
            case "non-student" -> "非学生（社会学习者）";
            default -> user.getPhase();
        };
        String typeName = "memory".equals(user.getUserType()) ? "记忆型（偏好深度记忆）" : "刷题型（偏好快速刷词）";

        // 构建 prompt
        String prompt = String.format("""
                你是一位英语学习规划师。请根据以下用户信息，给出一条简短的个性化学习建议。

                用户信息：
                - 学习阶段：%s
                - 学习类型：%s
                - 每日目标：%s 词/天
                - 已学单词：%s 词
                - 已掌握：%s 词
                - 连续打卡：%s 天
                - 今日已学：%s 词

                参考格式（标题4字左右，内容一句话）：
                {"title": "制定合理目标", "content": "根据您的学习阶段和类型，建议每天学习10-20个新单词，循序渐进。"}

                要求：
                1. 输出严格 JSON，包含 title 和 content 两个字段，不要 markdown 代码块
                2. title 限制在 4~6 个字，简洁有力
                3. content 一句话，控制在 30~50 字，具体可操作
                4. 结合用户的学习阶段和类型，有针对性
                5. 语气积极鼓励""",
                phaseName, typeName,
                planData.get("dailyGoal"), planData.get("totalWords"),
                planData.get("masteredWords"), planData.get("studyStreak"),
                planData.get("todayLearned"));

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmModel);
        requestBody.put("temperature", llmTemperature);
        requestBody.put("max_tokens", llmMaxTokens);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是一位英语学习规划助手。输出严格 JSON，格式如 {\"title\":\"标题\",\"content\":\"建议\"}。"));
        messages.add(Map.of("role", "user", "content", prompt));
        requestBody.put("messages", messages);

        // 发送请求
        String baseUrl = llmBaseUrl.replaceAll("/+$", "");
        if (!baseUrl.endsWith("/chat/completions")) {
            baseUrl += "/chat/completions";
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmApiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(baseUrl, HttpMethod.POST, entity, String.class);

        // 解析响应
        JsonNode root = objectMapper.readTree(response.getBody());
        String content = root.path("choices").get(0).path("message").path("content").asText();

        // 清理可能的 markdown 代码块
        content = content.replaceAll("(?s)```\\w*\\s*", "").trim();

        JsonNode tipJson = objectMapper.readTree(content);
        String title = tipJson.path("title").asText();
        String tipContent = tipJson.path("content").asText();

        if (title.isBlank() || tipContent.isBlank()) {
            throw new RuntimeException("LLM 返回内容不完整");
        }

        return Map.of("title", title, "content", tipContent);
    }

    /**
     * 随机返回一条预设建议
     */
    private Map<String, String> getRandomPredefinedTip() {
        return PREDEFINED_TIPS.get(random.nextInt(PREDEFINED_TIPS.size()));
    }

    private int calculateStreak(List<Interaction> interactions) {
        if (interactions.isEmpty()) return 0;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Set<String> dateSet = interactions.stream()
                .map(i -> sdf.format(new Date(i.getTimestamp())))
                .collect(Collectors.toSet());

        Calendar cal = Calendar.getInstance();
        String today = sdf.format(cal.getTime());

        // 如果今天没有交互，从昨天开始算
        if (!dateSet.contains(today)) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        int streak = 0;
        while (true) {
            String date = sdf.format(cal.getTime());
            if (dateSet.contains(date)) {
                streak++;
                cal.add(Calendar.DAY_OF_YEAR, -1);
            } else {
                break;
            }
        }
        return streak;
    }

    private long getTodayStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private int applyDecay(int storedFamiliarity, Long lastInteractionTime) {
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

    private List<Word> getAccessibleWords(User user) {
        List<Word> allWords = wordRepository.findAll();
        String userPhase = user.getPhase();
        String uid = user.getUserId();
        return allWords.stream()
                .filter(w -> (w.getWordType() == WordType.SYLLABUS && isPhaseMatch(w.getPhase(), userPhase))
                        || (w.getWordType() == WordType.CUSTOM && uid.equals(w.getUserId())))
                .collect(Collectors.toList());
    }

    private boolean isPhaseMatch(String wordPhase, String userPhase) {
        if (wordPhase == null) return true;
        switch (userPhase) {
            case "non-student": return false;
            case "primary": return "primary".equals(wordPhase);
            case "junior": return "primary".equals(wordPhase) || "junior".equals(wordPhase);
            case "senior": return true;
            default: return true;
        }
    }
}