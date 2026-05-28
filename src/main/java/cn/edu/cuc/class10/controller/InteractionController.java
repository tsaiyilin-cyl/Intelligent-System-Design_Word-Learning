package cn.edu.cuc.class10.controller;

import cn.edu.cuc.class10.service.InteractionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/interaction")
public class InteractionController {

    @Autowired
    private InteractionService interactionService;

    @PostMapping("/record")
    public Map<String, Object> recordInteraction(@RequestBody Map<String, String> payload) {
        String userId = payload.get("userId");
        String wordId = payload.get("wordId");
        String feedback = payload.get("feedback");

        Map<String, Object> result = new HashMap<>();
        if (userId == null || wordId == null || feedback == null) {
            result.put("code", 400);
            result.put("message", "参数缺失");
            return result;
        }
        if (!"known".equals(feedback) && !"unknown".equals(feedback)) {
            result.put("code", 400);
            result.put("message", "feedback 必须是 known 或 unknown");
            return result;
        }

        try {
            interactionService.recordInteraction(userId, wordId, feedback);
            result.put("code", 200);
            result.put("message", "记录成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "记录失败: " + e.getMessage());
        }
        return result;
    }
}