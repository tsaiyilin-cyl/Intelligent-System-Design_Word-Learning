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
     * 获取单词列表（供报告详情查看）
     * filter: all | mastered | unmastered
     */
    /**
     * 获取学习计划实时数据
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