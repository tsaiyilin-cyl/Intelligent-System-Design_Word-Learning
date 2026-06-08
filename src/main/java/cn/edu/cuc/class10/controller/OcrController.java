package cn.edu.cuc.class10.controller;

import cn.edu.cuc.class10.entity.Word;
import cn.edu.cuc.class10.entity.WordType;
import cn.edu.cuc.class10.service.OcrService;
import cn.edu.cuc.class10.service.WordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * 照片识词 REST 接口
 * 提供图片上传识别、词库添加、模型状态查询功能
 */
@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private static final Logger log = LoggerFactory.getLogger(OcrController.class);

    @Autowired
    private OcrService ocrService;

    @Autowired
    private WordService wordService;

    /**
     * 上传图片并识别图中物体
     * POST /api/ocr/recognize
     *
     * @param file   上传的图片文件 (multipart/form-data)
     * @param userId 用户 ID（可选，用于后续熟悉度追踪）
     * @return 识别候选词列表
     */
    @PostMapping("/recognize")
    public Map<String, Object> recognize(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) String userId) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 校验文件
            if (file.isEmpty()) {
                result.put("code", 400);
                result.put("message", "请上传图片文件");
                return result;
            }

            // 校验文件类型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                result.put("code", 400);
                result.put("message", "仅支持图片文件（jpg/png 等）");
                return result;
            }

            // 检查模型状态
            if (!ocrService.isModelReady()) {
                result.put("code", 503);
                result.put("message", ocrService.getModelError() != null
                        ? ocrService.getModelError() : "模型正在加载中，请稍后再试");
                return result;
            }

            // 执行识别
            log.info("Recognizing image: {} ({} bytes)", file.getOriginalFilename(), file.getSize());
            List<OcrService.RecognitionResult> candidates = ocrService.recognize(file.getInputStream());

            // 构建响应
            List<Map<String, Object>> dataList = new ArrayList<>();
            for (OcrService.RecognitionResult candidate : candidates) {
                Map<String, Object> item = new HashMap<>();
                item.put("label", candidate.getLabel());
                item.put("confidence", candidate.getConfidence());
                item.put("displayName", candidate.getDisplayName());
                item.put("matched", candidate.isMatched());

                if (candidate.isMatched()) {
                    item.put("wordId", candidate.getWordId());
                    item.put("content", candidate.getContent());
                    item.put("translation", candidate.getTranslation());
                    item.put("phonetic", candidate.getPhonetic());
                    item.put("partOfSpeech", candidate.getPartOfSpeech());
                } else {
                    item.put("wordId", null);
                    item.put("content", null);
                    item.put("translation", null);
                    item.put("phonetic", null);
                    item.put("partOfSpeech", null);
                }

                dataList.add(item);
            }

            result.put("code", 200);
            result.put("message", "识别成功");
            result.put("data", dataList);
            return result;

        } catch (IllegalStateException e) {
            log.warn("OCR model not ready: {}", e.getMessage());
            result.put("code", 503);
            result.put("message", e.getMessage());
            return result;
        } catch (IOException e) {
            log.error("Image read error", e);
            result.put("code", 400);
            result.put("message", "图片读取失败: " + e.getMessage());
            return result;
        } catch (Exception e) {
            log.error("Image recognition failed", e);
            result.put("code", 500);
            result.put("message", "识别出错: " + e.getClass().getSimpleName() + " - " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            return result;
        }
    }

    /**
     * 用户确认后将候选词加入词库（CUSTOM 类型）
     * POST /api/ocr/add-word
     *
     * @param body 请求体: { "word": "frog", "translation": "青蛙", "userId": "xxx" }
     * @return 操作结果
     */
    @PostMapping("/add-word")
    public Map<String, Object> addWord(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();

        try {
            String word = (String) body.get("word");
            String translation = (String) body.get("translation");
            String userId = (String) body.get("userId");

            if (word == null || word.trim().isEmpty()) {
                result.put("code", 400);
                result.put("message", "单词不能为空");
                return result;
            }

            if (userId == null || userId.trim().isEmpty()) {
                result.put("code", 400);
                result.put("message", "请先登录");
                return result;
            }

            // 净化单词内容：小写、去除首尾空白
            word = word.trim().toLowerCase();

            // 如果未提供释义，使用默认提示
            if (translation == null || translation.trim().isEmpty()) {
                translation = "（照片识词，待补充释义）";
            }

            // 创建 CUSTOM 类型的单词
            Word newWord = wordService.addWord(
                    word,
                    "NOUN",
                    translation,
                    null,
                    WordType.CUSTOM,
                    userId
            );

            result.put("code", 200);
            result.put("message", "已添加到词库");
            result.put("data", Map.of(
                    "wordId", newWord.getWordId(),
                    "content", newWord.getContent(),
                    "translation", newWord.getTranslation(),
                    "wordType", newWord.getWordType()
            ));
            return result;

        } catch (RuntimeException e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
            return result;
        } catch (Exception e) {
            log.error("Failed to add OCR word to library", e);
            result.put("code", 500);
            result.put("message", "添加失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 查询模型加载状态
     * GET /api/ocr/status
     *
     * @return 模型状态信息
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> result = new HashMap<>();

        result.put("code", 200);
        result.put("message", "查询成功");
        result.put("data", Map.of(
                "modelReady", ocrService.isModelReady(),
                "modelError", ocrService.getModelError() != null ? ocrService.getModelError() : "",
                "labelCount", ocrService.getValidLabelCount()
        ));
        return result;
    }
}
