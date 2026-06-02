package cn.edu.cuc.class10.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 单词图片服务 —— 免费方案：
 * 1. 优先检查本地缓存
 * 2. Unsplash 图库搜索（免费，适合具体名词）
 * 3. 下载保存到本地，后续直接使用缓存
 *
 * 支持配置多个 Unsplash API Key 轮流使用，每个 Key 独立计数，
 * 全部耗尽后自动熔断不再请求。
 */
@Service
public class WordImageService {

    private static final Logger log = LoggerFactory.getLogger(WordImageService.class);

    @Value("${word-images.directory:word-images}")
    private String imageDirectory;

    /** 多个 Unsplash API Key，用逗号分隔（每个 Key 每小时 50 次免费额度） */
    @Value("${word-images.unsplash.access-keys:}")
    private String unsplashAccessKeys;

    /** 每个 Key 的调用上限（Unsplash 免费额度 50 次/小时） */
    @Value("${word-images.unsplash.max-calls-per-key:50}")
    private int maxCallsPerKey;

    @Autowired
    private RestTemplate restTemplate;

    // ===== 多 Key 轮换状态 =====
    /** 解析后的 Key 列表 */
    private volatile List<String> parsedKeys = List.of();
    /** 总可用次数上限 = keys.size() × maxCallsPerKey */
    private volatile int totalCallLimit = 0;
    /** 全局调用序号（AtomicInteger 保证线程安全，绝不丢失） */
    private final AtomicInteger totalCallSeq = new AtomicInteger(0);

    /**
     * 延迟初始化 Key 列表
     */
    private void ensureKeysInitialized() {
        if (!parsedKeys.isEmpty() || totalCallLimit > 0) return;
        if (!StringUtils.hasText(unsplashAccessKeys)) return;

        List<String> keys = Arrays.stream(unsplashAccessKeys.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (keys.isEmpty()) return;

        parsedKeys = List.copyOf(keys);
        totalCallLimit = keys.size() * maxCallsPerKey;
        log.info("Unsplash 多 Key 模式，共 {} 个 Key，总上限 {} 次（每个 {} 次/小时）",
                keys.size(), totalCallLimit, maxCallsPerKey);
    }

    /**
     * 获取该次调用应使用的 Key 索引
     * @return 第几次调用（从 1 开始），上限后返回 -1
     */
    private int acquireCallSlot() {
        // getAndIncrement 是原子的，多线程下也不会丢失
        int seq = totalCallSeq.getAndIncrement();
        if (seq >= totalCallLimit) {
            log.warn("总调用次数已达上限 {}/{}，跳过后续搜索", totalCallLimit, totalCallLimit);
            return -1;
        }
        return seq;
    }

    /**
     * 获取单词图片 URL（懒加载 + 缓存）
     * <p>
     * 搜索链：本地缓存 → Unsplash → Bing 图片搜索 → 备用来源
     *
     * @param wordId      单词 ID
     * @param wordContent 单词内容（英文）
     * @param translation 中文释义（用于中文搜索兜底）
     * @return 图片的静态资源路径（如 /word-images/xxx.jpg），无可用的图片时返回 null
     */
    public String getOrCreateImage(String wordId, String wordContent, String translation) {
        // 1. 检查本地缓存
        File imageFile = getImageFile(wordId);
        if (imageFile.exists() && imageFile.length() > 0) {
            return "/word-images/" + sanitizeFileName(wordId) + ".jpg";
        }

        // 确保目录存在
        File dir = new File(imageDirectory);
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("无法创建图片目录: {}", imageDirectory);
            return null;
        }

        // 2. 尝试 Unsplash 搜索（多个 Key 顺序轮换）
        String result = tryUnsplash(wordContent, imageFile);
        if (result != null) return result;

        // 3. Unsplash 失败 → 尝试 Bing 图片搜索（中文释义优先，英文兜底）
        result = tryBing(wordContent, translation, imageFile);
        if (result != null) return result;

        return null;
    }

    /**
     * 判断单词是否有本地图片缓存
     */
    public boolean hasLocalImage(String wordId) {
        File file = getImageFile(wordId);
        return file.exists() && file.length() > 0;
    }

    /**
     * 删除单词本地缓存的图片
     */
    public boolean deleteLocalImage(String wordId) {
        File file = getImageFile(wordId);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                log.info("已删除单词图片缓存 [{}]", wordId);
            } else {
                log.warn("删除单词图片缓存失败 [{}]", wordId);
            }
            return deleted;
        }
        return false;
    }

    /**
     * 删除旧图片并异步重新生成
     */
    public void regenerateImageAsync(String wordId, String wordContent, String translation) {
        deleteLocalImage(wordId);
        imagePreloader.submit(() -> {
            try {
                getOrCreateImage(wordId, wordContent, translation);
                log.info("单词图片重新生成完成 [{}]", wordContent);
            } catch (Exception e) {
                log.warn("单词图片重新生成失败 [{}]: {}", wordContent, e.getMessage());
            }
        });
    }

    // ==================== 异步预加载 ====================

    private final ExecutorService imagePreloader = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "image-preloader");
        t.setDaemon(true);
        return t;
    });

    /**
     * 后台异步预加载单词图片（不阻塞调用方）
     * 已缓存的图片会跳过，只有缺失的才去 Unsplash 搜索下载
     */
    public void preloadAsync(String wordId, String wordContent, String translation) {
        if (hasLocalImage(wordId)) return; // 已有缓存，跳过
        imagePreloader.submit(() -> {
            try {
                getOrCreateImage(wordId, wordContent, translation);
            } catch (Exception e) {
                log.warn("后台预加载图片失败 [{}]: {}", wordContent, e.getMessage());
            }
        });
    }

    /**
     * 批量后台异步预加载
     */
    public void preloadBatchAsync(List<PreloadTask> tasks) {
        for (PreloadTask task : tasks) {
            preloadAsync(task.wordId, task.wordContent, task.translation);
        }
    }

    /**
     * 预加载任务（简单 POJO）
     */
    public record PreloadTask(String wordId, String wordContent, String translation) {}

    // ==================== Unsplash 搜索 ====================

    /**
     * 尝试从 Unsplash 获取图片
     */
    private String tryUnsplash(String keyword, File targetFile) {
        ensureKeysInitialized();
        if (parsedKeys.isEmpty()) {
            log.info("未配置 Unsplash Access Key，跳过图库搜索");
            return null;
        }

        int seq = acquireCallSlot();
        if (seq < 0) {
            return null; // 所有 Key 额度已用完
        }

        int keyIdx = seq / maxCallsPerKey;
        int callInKey = seq % maxCallsPerKey + 1;
        if (keyIdx >= parsedKeys.size()) {
            log.warn("所有 Unsplash API Key 均已达到上限，跳过后续搜索");
            return null;
        }

        String key = parsedKeys.get(keyIdx);
        try {
            log.info("Unsplash Key {} 调用第 {}/{} 次，搜索: {}", keyIdx + 1, callInKey, maxCallsPerKey, keyword);
            String imageUrl = searchUnsplash(key, keyword);
            if (imageUrl != null) {
                downloadImage(imageUrl, targetFile);
                if (targetFile.exists() && targetFile.length() > 0) {
                    return "/word-images/" + sanitizeFileName(targetFile.getName().replace(".jpg", "")) + ".jpg";
                }
            }
        } catch (Exception e) {
            log.warn("Unsplash 搜索失败 [Key {}] [{}]: {}", keyIdx + 1, keyword, e.getMessage());
        }
        return null;
    }

    /**
     * 使用指定 Key 调用 Unsplash 搜索
     */
    private String searchUnsplash(String apiKey, String query) {
        String url = "https://api.unsplash.com/search/photos?query={query}&per_page=1&orientation=landscape";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Client-ID " + apiKey);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class, query);

        if (response.getBody() == null || !response.getStatusCode().is2xxSuccessful()) {
            return null;
        }

        List<Map> results = (List<Map>) response.getBody().get("results");
        if (results == null || results.isEmpty()) {
            log.info("Unsplash 未找到与 [{}] 相关的图片", query);
            return null;
        }

        Map urls = (Map) results.get(0).get("urls");
        if (urls == null) return null;

        // 优先用 regular 尺寸，fallback 到 raw
        String imageUrl = (String) urls.get("regular");
        if (imageUrl == null) {
            imageUrl = (String) urls.get("raw");
        }
        return imageUrl;
    }

    // ==================== 本地文件操作 ====================

    private File getImageFile(String wordId) {
        return new File(imageDirectory, sanitizeFileName(wordId) + ".jpg");
    }

    /**
     * 从 URL 下载图片保存到本地文件
     */
    private void downloadImage(String imageUrl, File targetFile) {
        try {
            // 创建临时文件先下载，避免下载中断留下不完整文件
            File tempFile = new File(targetFile.getAbsolutePath() + ".tmp");

            RequestEntity<Void> request = RequestEntity.get(URI.create(imageUrl))
                    .header("User-Agent", "WordManager/1.0")
                    .build();

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    request, byte[].class);

            if (response.getBody() != null && response.getStatusCode().is2xxSuccessful()) {
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(response.getBody());
                }
                // 下载完成，重命名为目标文件名
                if (tempFile.renameTo(targetFile)) {
                    log.info("图片已保存: {}", targetFile.getAbsolutePath());
                } else {
                    // rename 失败（可能跨分区），直接写入目标文件
                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        fos.write(response.getBody());
                    }
                    tempFile.delete();
                }
            }
        } catch (Exception e) {
            log.error("下载图片失败 [{}]: {}", imageUrl, e.getMessage());
        }
    }

    /**
     * 尝试从 Bing 图片搜索获取图片（中文/英文双语言搜索）
     */
    private String tryBing(String english, String chinese, File targetFile) {
        // 中文释义优先搜索
        if (StringUtils.hasText(chinese)) {
            String kw = chinese.length() > 20 ? chinese.substring(0, 20) : chinese;
            log.info("Bing 搜索（中文）: {}", kw);
            if (doBingSearch(kw, targetFile)) return imageUrlPath(targetFile);
        }
        // 英文兜底
        log.info("Bing 搜索（英文）: {}", english);
        if (doBingSearch(english, targetFile)) return imageUrlPath(targetFile);
        return null;
    }

    /**
     * 执行一次 Bing 图片搜索并下载第一张有效图片
     */
    private boolean doBingSearch(String keyword, File targetFile) {
        try {
            String url = "https://cn.bing.com/images/search?q={keyword}&form=HDRSC2&first=1&count=5";
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            headers.set("Accept-Language", "zh-CN,zh;q=0.9");
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class, keyword);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return false;
            }

            // 从 HTML 中提取 mediaurl 参数
            Pattern pattern = Pattern.compile("mediaurl=(.*?)[&\"]");
            Matcher matcher = pattern.matcher(response.getBody());
            List<String> imageUrls = new ArrayList<>();
            while (matcher.find()) {
                String decoded = java.net.URLDecoder.decode(matcher.group(1), "UTF-8");
                if (decoded.length() < 500 && !imageUrls.contains(decoded)) {
                    imageUrls.add(decoded);
                }
            }

            // 逐个尝试下载
            for (String imgUrl : imageUrls) {
                // 跳过 data: URI 和小图标
                if (imgUrl.startsWith("data:") || imgUrl.length() < 20) continue;
                try {
                    downloadImage(imgUrl, targetFile);
                    if (targetFile.exists() && targetFile.length() > 2000) {
                        log.info("Bing 下载成功: {} -> {}", keyword, targetFile.getName());
                        return true;
                    }
                } catch (Exception ignored) {
                    // 单张失败继续试下一张
                }
            }
        } catch (Exception e) {
            log.warn("Bing 搜索失败 [{}]: {}", keyword, e.getMessage());
        }
        return false;
    }

    /**
     * 根据目标文件生成图片 URL 路径
     */
    private String imageUrlPath(File file) {
        return "/word-images/" + sanitizeFileName(file.getName().replace(".jpg", "")) + ".jpg";
    }

    /**
     * 清理文件名中的特殊字符
     */
    private String sanitizeFileName(String name) {
        if (name == null) return "null";
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
