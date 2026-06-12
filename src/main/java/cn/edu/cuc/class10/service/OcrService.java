package cn.edu.cuc.class10.service;

import cn.edu.cuc.class10.entity.Word;
import cn.edu.cuc.class10.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 照片识词核心服务
 *
 * 【架构变更说明】
 * 原实现：使用 DJL 本地加载 PyTorch 模型进行推理（700+ 行复杂代码）
 * 现实现：调用 Flask AI 服务进行推理，数据库单词匹配仍保留在本地
 *
 * Flask 负责：
 *   - 模型加载 / 管理 / 自动下载
 *   - 图片预处理 + TorchScript 推理
 *   - 依赖检查与自动安装（跨机器部署）
 *
 * Spring Boot 负责：
 *   - 图片上传接收
 *   - 调用 Flask 获取分类结果
 *   - 数据库单词精确匹配
 *   - 响应格式化
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    @Value("${flask.base-url:http://localhost:5000}")
    private String flaskBaseUrl;

    @Value("${flask.connect-timeout:5000}")
    private int flaskConnectTimeout;

    @Value("${flask.read-timeout:10000}")
    private int flaskReadTimeout;

    @Value("${ocr.top-k:5}")
    private int topK;

    @Value("${ocr.confidence-threshold:0.3}")
    private float confidenceThreshold;

    @Autowired
    private WordRepository wordRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    // ==================== Flask 远程调用 ====================

    /**
     * 调用 Flask AI 服务进行图片识别
     *
     * @param imageBytes 图片原始字节
     * @return Flask 返回的原始 JSON（含 label / confidence / classIndex）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callFlaskOcr(byte[] imageBytes) {
        String url = flaskBaseUrl.replaceAll("/+$", "") + "/api/ocr/recognize";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        HttpEntity<byte[]> entity = new HttpEntity<>(imageBytes, headers);

        // 为 OCR 请求单独设置超时（大图片可能需要更多时间）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(flaskConnectTimeout);
        factory.setReadTimeout(flaskReadTimeout * 3); // OCR 推理较慢，超时放宽到 3 倍
        RestTemplate ocrRestTemplate = new RestTemplate(factory);

        ResponseEntity<Map> response = ocrRestTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);

        Map<String, Object> body = response.getBody();
        if (body == null || !Integer.valueOf(200).equals(body.get("code"))) {
            String msg = body != null ? (String) body.get("message") : "null response";
            throw new RuntimeException("Flask OCR 返回异常: " + msg);
        }

        return body;
    }

    /**
     * 查询 Flask OCR 模型状态
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchFlaskOcrStatus() {
        String url = flaskBaseUrl.replaceAll("/+$", "") + "/api/ocr/status";
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && Integer.valueOf(200).equals(body.get("code"))) {
                return (Map<String, Object>) body.get("data");
            }
        } catch (Exception e) {
            log.warn("Flask OCR 状态查询失败: {}", e.getMessage());
        }
        return null;
    }

    // ==================== 核心推理入口 ====================

    /**
     * 识别图片中的物体
     *
     * @param imageStream 图片输入流
     * @return 识别结果列表（按置信度降序），已匹配数据库单词
     * @throws RuntimeException Flask 不可用或推理失败时抛出
     */
    public List<RecognitionResult> recognize(InputStream imageStream) {
        try {
            // 1. 读取图片字节
            byte[] imageBytes = readBytes(imageStream);

            // 2. 调用 Flask 获取分类候选
            Map<String, Object> flaskResponse = callFlaskOcr(imageBytes);
            List<Map<String, Object>> rawResults = (List<Map<String, Object>>) flaskResponse.get("data");
            if (rawResults == null) {
                return Collections.emptyList();
            }

            // 3. 匹配数据库单词
            List<RecognitionResult> results = new ArrayList<>();
            for (Map<String, Object> raw : rawResults) {
                String label = (String) raw.get("label");
                Object confObj = raw.get("confidence");
                float confidence = confObj instanceof Number ? ((Number) confObj).floatValue() : 0f;

                if (confidence < confidenceThreshold) continue;

                String displayName = formatDisplayName(label);

                RecognitionResult result = new RecognitionResult();
                result.setLabel(label);
                result.setConfidence(confidence);
                result.setDisplayName(displayName);

                searchWordInDatabase(result, displayName);

                results.add(result);
            }

            // 4. 按置信度降序排列
            results.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
            return results;

        } catch (IOException e) {
            log.error("图片读取失败", e);
            throw new RuntimeException("图片读取失败: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("图片识别失败", e);
            throw e;  // 直接透传
        } catch (Exception e) {
            log.error("图片识别异常", e);
            throw new RuntimeException("图片识别失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将 InputStream 读取为字节数组
     */
    private byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) != -1) {
            buffer.write(tmp, 0, n);
        }
        return buffer.toByteArray();
    }

    // ==================== 标签处理 ====================

    /**
     * 清洗 DJL/Flask 返回的类名，去除 synset ID 等前缀
     */
    private String cleanLabel(String className) {
        if (className == null) return "";

        // 去除 "n01440764 tench" 格式的 synset ID 前缀
        String cleaned = className.replaceAll("^n\\d+\\s+", "");
        // 去除 "0: tench" 格式的数字前缀
        cleaned = cleaned.replaceAll("^\\d+[\\s,:]+", "");
        // 去除首尾空白
        cleaned = cleaned.trim();

        return cleaned;
    }

    /**
     * 将标签名格式化为可读形式
     * "tree_frog" → "tree frog"; "Tench" → "tench"
     */
    private String formatDisplayName(String label) {
        if (label == null || label.isEmpty()) return "";
        return label.toLowerCase()
                .replace("_", " ")
                .replace("-", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // ==================== 数据库匹配 ====================

    /**
     * 在 words 表中搜索与识别标签匹配的单词
     * 匹配策略：精确匹配（case-insensitive）
     */
    private void searchWordInDatabase(RecognitionResult result, String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            result.setMatched(false);
            return;
        }

        Optional<Word> exactMatch = wordRepository.findByContent(displayName);
        if (exactMatch.isPresent()) {
            fillMatchedWord(result, exactMatch.get());
        } else {
            result.setMatched(false);
        }
    }

    private void fillMatchedWord(RecognitionResult result, Word word) {
        result.setMatched(true);
        result.setWordId(word.getWordId());
        result.setContent(word.getContent());
        result.setTranslation(word.getTranslation());
        result.setPhonetic(word.getPhonetic());
        result.setPartOfSpeech(word.getPartOfSpeech());
    }

    // ==================== 状态查询 ====================

    /**
     * 检查 Flask OCR 模型是否就绪
     */
    public boolean isModelReady() {
        Map<String, Object> status = fetchFlaskOcrStatus();
        return status != null && Boolean.TRUE.equals(status.get("modelReady"));
    }

    /**
     * 获取 Flask OCR 模型的错误信息
     */
    public String getModelError() {
        Map<String, Object> status = fetchFlaskOcrStatus();
        if (status == null) {
            return "无法连接到 Flask AI 服务";
        }
        Object err = status.get("modelError");
        return err instanceof String ? (String) err : "";
    }

    /**
     * 获取有效的标签数量
     */
    public int getValidLabelCount() {
        Map<String, Object> status = fetchFlaskOcrStatus();
        if (status == null) return 0;
        Object count = status.get("labelCount");
        return count instanceof Number ? ((Number) count).intValue() : 0;
    }

    // ==================== 内部类 - 识别结果 ====================

    /**
     * 单条识别结果，包含模型输出和数据库匹配信息
     */
    public static class RecognitionResult {
        /** 模型原始输出标签名（如 "tree frog"） */
        private String label;
        /** 置信度（0~1） */
        private float confidence;
        /** 格式化后的显示名称（如 "tree frog"） */
        private String displayName;
        /** 是否在 words 表中匹配到单词 */
        private boolean matched;
        /** 匹配到的单词 ID */
        private String wordId;
        /** 匹配到的单词内容 */
        private String content;
        /** 中文释义 */
        private String translation;
        /** 音标 */
        private String phonetic;
        /** 词性 */
        private String partOfSpeech;

        // ===== Getters & Setters =====

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public float getConfidence() { return confidence; }
        public void setConfidence(float confidence) { this.confidence = confidence; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public boolean isMatched() { return matched; }
        public void setMatched(boolean matched) { this.matched = matched; }

        public String getWordId() { return wordId; }
        public void setWordId(String wordId) { this.wordId = wordId; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getTranslation() { return translation; }
        public void setTranslation(String translation) { this.translation = translation; }

        public String getPhonetic() { return phonetic; }
        public void setPhonetic(String phonetic) { this.phonetic = phonetic; }

        public String getPartOfSpeech() { return partOfSpeech; }
        public void setPartOfSpeech(String partOfSpeech) { this.partOfSpeech = partOfSpeech; }
    }
}
