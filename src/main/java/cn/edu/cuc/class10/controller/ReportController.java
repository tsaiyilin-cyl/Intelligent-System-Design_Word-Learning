package cn.edu.cuc.class10.controller;

import cn.edu.cuc.class10.dto.DashboardDataResponse;
import cn.edu.cuc.class10.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    /**
     * 获取主页仪表盘数据（单词统计、正确率、近期测试记录、易错词TOP5等）
     * GET /api/report/dashboard
     *
     * @param userId 用户ID
     * @return 仪表盘聚合数据
     */
    @GetMapping("/dashboard")
    public Map<String, Object> getDashboardData(@RequestParam String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            DashboardDataResponse data = reportService.getDashboardData(userId);
            result.put("code", 200);
            result.put("data", data);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 获取学习计划实时数据（单词统计、连续打卡、今日学习数等）
     * GET /api/report/studyPlan
     */
    @GetMapping("/studyPlan")
    public Map<String, Object> getStudyPlan(@RequestParam String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = reportService.getStudyPlanData(userId);
            result.put("code", 200);
            result.put("data", data);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 获取学习建议（一条）
     * 优先调用大模型生成个性化建议，失败则随机返回预设建议
     * GET /api/report/studyTip
     */
    @GetMapping("/studyTip")
    public Map<String, Object> getStudyTip(@RequestParam String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> tip = reportService.getStudyTip(userId);
            result.put("code", 200);
            result.put("data", tip);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 获取单词列表（供报告详情查看）
     * filter: all | mastered | unmastered | learned
     * GET /api/report/wordList
     */
    @GetMapping("/wordList")
    public Map<String, Object> getWordList(
            @RequestParam String userId,
            @RequestParam(defaultValue = "all") String filter) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> words = reportService.getWordList(userId, filter);
            result.put("code", 200);
            result.put("data", words);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }
}