package cn.edu.cuc.class10.controller;

import cn.edu.cuc.class10.dto.GenerateTestRequest;
import cn.edu.cuc.class10.dto.SubmitAnswerRequest;
import cn.edu.cuc.class10.dto.TestQuestion;
import cn.edu.cuc.class10.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private TestService testService;

    /**
     * 生成测试题目
     * POST /api/test/generate
     * 根据用户阶段和用户类型加权选题（记忆型/刷题型），支持多种题型
     *
     * @param request { userId, count, questionType }
     * @return 含 sessionId 和题目列表
     */
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody GenerateTestRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> generateResult = testService.generateQuestions(
                    request.getUserId(),
                    request.getCount(),
                    request.getQuestionType()
            );
            result.put("code", 200);
            result.put("data", generateResult);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 提交单题答案（交由 Service 记录交互并自动更新熟悉度）
     * POST /api/test/submit
     *
     * @param request { userId, wordId, userAnswer, correctAnswer }
     * @return { code, correct: true/false }
     */
    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody SubmitAnswerRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean isCorrect = testService.submitAnswer(
                    request.getUserId(),
                    request.getWordId(),
                    request.getUserAnswer(),
                    request.getCorrectAnswer()
            );
            result.put("code", 200);
            result.put("correct", isCorrect);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 保存整个测试结果（生成 TestSession 记录）
     * POST /api/test/saveResult
     *
     * @param payload { userId, questionType, totalQuestions, correctCount, startTime, endTime, sessionId }
     * @return { code, sessionId }
     */
    @PostMapping("/saveResult")
    public Map<String, Object> saveResult(@RequestBody Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            String userId = (String) payload.get("userId");
            String questionType = (String) payload.get("questionType");
            Integer totalQuestions = (Integer) payload.get("totalQuestions");
            Integer correctCount = (Integer) payload.get("correctCount");
            Long startTime = ((Number) payload.get("startTime")).longValue();
            Long endTime = ((Number) payload.get("endTime")).longValue();
            String sessionId = (String) payload.get("sessionId");  // 从前端接收 sessionId

            if (userId == null || totalQuestions == null || correctCount == null) {
                result.put("code", 400);
                result.put("message", "参数缺失");
                return result;
            }

            // 如果前端传了 sessionId，就使用它；否则后端生成新的
            String savedSessionId = testService.saveTestResult(userId, questionType, totalQuestions, correctCount, startTime, endTime, sessionId);
            result.put("code", 200);
            result.put("message", "测试结果已保存");
            result.put("sessionId", savedSessionId);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 获取测试会话详情（含每道题的答题记录）
     * GET /api/test/detail/{sessionId}
     */
    @GetMapping("/detail/{sessionId}")
    public Map<String, Object> getDetail(@PathVariable String sessionId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> detail = testService.getTestDetail(sessionId);
            result.put("code", 200);
            result.put("data", detail);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 保存单道题的详细答题记录（用于后续回顾错题）
     * POST /api/test/saveAnswerRecord
     *
     * @param payload { sessionId, questionIndex, wordId, questionType, ... }
     */
    @PostMapping("/saveAnswerRecord")
    public Map<String, Object> saveAnswerRecord(@RequestBody Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            String sessionId = (String) payload.get("sessionId");
            Integer questionIndex = (Integer) payload.get("questionIndex");
            String wordId = (String) payload.get("wordId");
            String questionType = (String) payload.get("questionType");
            String questionContent = (String) payload.get("questionContent");
            String correctAnswer = (String) payload.get("correctAnswer");
            String userAnswer = (String) payload.get("userAnswer");
            Boolean isCorrect = (Boolean) payload.get("isCorrect");
            String optionsJson = (String) payload.get("optionsJson");

            if (sessionId == null || questionIndex == null) {
                result.put("code", 400);
                result.put("message", "参数缺失");
                return result;
            }

            testService.saveAnswerRecord(
                    sessionId, questionIndex, wordId, questionType, questionContent,
                    correctAnswer, userAnswer, isCorrect, optionsJson
            );
            result.put("code", 200);
            result.put("message", "答题记录已保存");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }
}