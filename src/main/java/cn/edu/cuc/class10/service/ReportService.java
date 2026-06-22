package cn.edu.cuc.class10.service;

import cn.edu.cuc.class10.dto.DashboardDataResponse;
import cn.edu.cuc.class10.entity.*;
import cn.edu.cuc.class10.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

    @Value("${flask.base-url:http://localhost:5000}")
    private String flaskBaseUrl;

    @Value("${flask.connect-timeout:5000}")
    private int flaskConnectTimeout;

    @Value("${flask.read-timeout:10000}")
    private int flaskReadTimeout;

    private static final List<Map<String, String>> PREDEFINED_TIPS = List.of(
            Map.of("title", "制定合理目标", "content", "根据您的学习阶段和类型，建议每天学习10-20个新单词，循序渐进。"),
            Map.of("title", "定期复习", "content", "利用艾宾浩斯遗忘曲线，在适当的时间间隔复习已学单词，提高记忆效果。"),
            Map.of("title", "保持连续性", "content", "每天坚持学习，即使时间不长，也能形成良好的学习习惯，提升学习效果。"),
            Map.of("title", "多样化学习", "content", "结合单词卡片、测试练习等多种方式，全方位巩固单词记忆。")
    );

    private final Random random = new Random();

    /**
     * 获取仪表盘聚合数据
     * 包含：总词数、掌握词数、近7天正确率趋势、最近5次测试记录、易错词TOP5
     * 熟悉度经过艾宾浩斯时间衰减后计算
     * 可访问词范围：考纲词（按阶段匹配） + 当前用户的自建词
     */
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
     * 获取学习建议（优先调用 Flask AI 服务，失败则使用预设建议）
     */
    public Map<String, Object> getStudyTip(String userId) {
        // 1. 获取用户数据（仅用于校验用户存在）
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 2. 获取学习统计数据
        Map<String, Object> planData = getStudyPlanData(userId);

        // 3. 尝试调用 Flask AI 服务
        try {
            return callFlaskForTip(userId, planData);
        } catch (Exception e) {
            log.warn("Flask AI 服务不可用，使用预设建议: {}", e.getMessage());
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
     * 调用 Flask AI 服务生成个性化学习建议
     */
    private Map<String, Object> callFlaskForTip(String userId, Map<String, Object> planData) throws Exception {
        String url = flaskBaseUrl.replaceAll("/+$", "") + "/api/study-tip";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("userId", userId);
        requestBody.put("studyData", planData);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // 设置超时
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(flaskConnectTimeout);
        requestFactory.setReadTimeout(flaskReadTimeout);
        RestTemplate flaskRestTemplate = new RestTemplate(requestFactory);

        ResponseEntity<Map> response = flaskRestTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
        Map<String, Object> body = response.getBody();
        if (body == null || !Integer.valueOf(200).equals(body.get("code"))) {
            throw new RuntimeException("Flask 返回异常: " + (body != null ? body.get("message") : "null"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        if (data == null || data.get("title") == null || data.get("content") == null) {
            throw new RuntimeException("Flask 返回数据不完整");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("title", data.get("title"));
        result.put("content", data.get("content"));
        result.put("source", data.getOrDefault("source", "ai"));
        return result;
    }

    /**
     * 随机返回一条预设建议
     */
    private Map<String, String> getRandomPredefinedTip() {
        return PREDEFINED_TIPS.get(random.nextInt(PREDEFINED_TIPS.size()));
    }

    /**
     * 计算连续打卡天数（基于交互记录日期，支持断签后归零）
     */
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