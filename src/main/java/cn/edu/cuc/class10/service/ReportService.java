package cn.edu.cuc.class10.service;

import cn.edu.cuc.class10.dto.DashboardDataResponse;
import cn.edu.cuc.class10.entity.*;
import cn.edu.cuc.class10.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

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

        // 3. 掌握单词数（熟悉度>=70）
        long masteredCount = accessibleWords.stream()
                .filter(w -> familiarityMap.getOrDefault(w.getWordId(), 50) >= 70)
                .count();

        // 4. 近7天测试记录和正确率
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

        // 5. 最近5次测试记录
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

        // 6. 易错词TOP5：使用 SQL 聚合查询（仅查前5个）
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

    private List<Word> getAccessibleWords(User user) {
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
}