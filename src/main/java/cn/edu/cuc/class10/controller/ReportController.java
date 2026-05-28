package cn.edu.cuc.class10.controller;

import cn.edu.cuc.class10.dto.DashboardDataResponse;
import cn.edu.cuc.class10.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
}