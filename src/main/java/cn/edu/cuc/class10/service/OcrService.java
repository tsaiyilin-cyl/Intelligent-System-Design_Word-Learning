package cn.edu.cuc.class10.service;

import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.transform.Normalize;
import ai.djl.modality.cv.transform.Resize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.modality.cv.translator.ImageClassificationTranslator;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import cn.edu.cuc.class10.entity.Word;
import cn.edu.cuc.class10.repository.WordRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 照片识词核心服务
 * 使用 DJL 加载预训练 ResNet 模型进行图片分类
 * 识别结果与 words 表匹配，推荐对应英文单词
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    @Value("${ocr.model-directory:ocr-models}")
    private String modelDirectory;

    @Value("${ocr.labels-file:synset_21k.txt}")
    private String labelsFile;

    @Value("${ocr.top-k:3}")
    private int topK;

    @Value("${ocr.confidence-threshold:0.3}")
    private float confidenceThreshold;

    @Value("${ocr.model-name:ResNet-50}")
    private String modelName;

    @Value("${ocr.model-url:}")
    private String modelUrl;

    @Autowired
    private WordRepository wordRepository;

    private ZooModel<Image, Classifications> model;
    private Predictor<Image, Classifications> predictor;

    /** 从 synset_21k.txt 加载的有效标签集合（小写） */
    private final Set<String> validLabels = new HashSet<>();

    /** 按模型输出顺序的标签列表（用于 Translator 的 synset 映射） */
    private List<String> synsetLabels = new ArrayList<>();

    private volatile boolean modelReady = false;
    private String modelError;

    /**
     * 应用启动时初始化：加载标签文件 → 加载模型
     */
    @PostConstruct
    public void init() {
        try {
            // 1. 确保模型目录存在
            File modelDir = new File(modelDirectory);
            if (!modelDir.exists()) {
                modelDir.mkdirs();
                log.info("Created model directory: {}", modelDir.getAbsolutePath());
            }

            // 2. 加载 synset 标签
            loadSynsetLabels();
            loadSynsetList();  // 加载有序列表供 translator 使用

            // 3. 加载预训练分类模型
            log.info("Loading OCR model: {}...", modelName);
            loadClassificationModel();

            modelReady = true;
            log.info("OCR model loaded successfully: {}", modelName);
        } catch (Exception e) {
            modelError = "模型加载失败: " + e.getMessage();
            log.error("Failed to load OCR model", e);
        }
    }

    // ==================== 模型加载（多策略） ====================

    /** DJL 模型仓库中 ResNet-50 的下载 URL（备用，当 model zoo 不可用时） */
    private static final String MODEL_REPO_URL =
            "https://mlrepo.djl.ai/model/cv/image_classification/ai/djl/zoo/resnet/0.0.5/resnet-50.zip";

    private void loadSynsetLabels() throws IOException {
        String resourcePath = labelsFile;
        InputStream is = null;

        // 先从 classpath 加载
        is = getClass().getClassLoader().getResourceAsStream(resourcePath);

        // 再从外部文件加载
        if (is == null) {
            File externalFile = new File(modelDirectory, resourcePath);
            if (externalFile.exists()) {
                is = new FileInputStream(externalFile);
            }
        }

        if (is == null) {
            log.warn("Synset file not found: {}, labels will be empty", resourcePath);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String label = line;
                if (label.matches("^n\\d+\\s+.*")) {
                    label = label.replaceAll("^n\\d+\\s+", "");
                }
                label = cleanLabel(label);
                if (!label.isEmpty()) {
                    validLabels.add(label.toLowerCase());
                }
            }
        }
        log.info("Loaded {} valid labels from synset file", validLabels.size());
    }

    /**
     * 加载有序的 synset 标签列表（用于 ImageClassificationTranslator 的类别映射）
     * 加载方式与 loadSynsetLabels() 相同，但保持列表顺序
     */
    private void loadSynsetList() throws IOException {
        List<String> labels = new ArrayList<>();
        InputStream is = null;

        // 先从 classpath 加载
        is = getClass().getClassLoader().getResourceAsStream(labelsFile);

        // 再从外部文件加载
        if (is == null) {
            File externalFile = new File(modelDirectory, labelsFile);
            if (externalFile.exists()) {
                is = new FileInputStream(externalFile);
            }
        }

        if (is == null) {
            log.warn("Synset file not found: {}, ordered list will be empty", labelsFile);
            synsetLabels = labels;
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                // 直接添加（保留原始名称，不去除下划线——给 translator 使用）
                labels.add(line);
            }
        }
        synsetLabels = Collections.unmodifiableList(labels);
        log.info("Loaded {} ordered labels for synset", synsetLabels.size());
    }

    /**
     * 策略 0: 从本地 TorchScript .pt 文件加载模型 (最高优先级)
     *
     * 使用 Python + timm 导出的 ImageNet-21k 模型文件,
     * 配合 ImageClassificationTranslator 进行图像预处理和输出映射。
     * 模型文件路径: &lt;model-directory&gt;/&lt;model-name&gt;.pt
     */
    private boolean tryLoadTorchScript(String ptPath) {
        try {
            Path path = Paths.get(ptPath);
            if (!Files.exists(path)) {
                log.info("TorchScript model not found at: {} (will try other strategies)", path.toAbsolutePath());
                return false;
            }

            log.info("Trying: load TorchScript model from {} ({} classes, topK={})",
                    ptPath, synsetLabels.size(), topK);

            // ImageNet 标准化参数
            float[] mean = {0.485f, 0.456f, 0.406f};
            float[] std  = {0.229f, 0.224f, 0.225f};

            // 构建图像分类 translator
            var translator = ImageClassificationTranslator.builder()
                    .addTransform(new Resize(224, 224))
                    .addTransform(new ToTensor())
                    .addTransform(new Normalize(mean, std))
                    .optSynset(synsetLabels)
                    .optTopK(topK)
                    .build();

            Criteria<Image, Classifications> criteria = Criteria.builder()
                    .setTypes(Image.class, Classifications.class)
                    .optModelPath(path)
                    .optEngine("PyTorch")
                    .optTranslator(translator)
                    .optOption("mapLocation", "true")
                    .build();

            model = ModelZoo.loadModel(criteria);
            predictor = model.newPredictor();
            log.info("OK Model loaded from TorchScript: {} ({} classes)", ptPath, synsetLabels.size());
            return true;
        } catch (Exception e) {
            log.warn("TorchScript load failed: {} (will try next strategy)", e.getMessage());
            return false;
        }
    }

    /**
     * 多策略模型加载：
     * 0) 本地 TorchScript .pt 文件 (ImageNet-21k, 最高优先级)
     * 1) PyTorch + Zoo (group=ai.djl.zoo, artifact=resnet, layers=50)
     * 2) PyTorch + optModelUrls (从 DJL 仓库下载 ZIP)
     * 3) 本地缓存文件加载
     * 4) 自动检测
     */
    private void loadClassificationModel() throws Exception {
        // 策略 0: 加载本地 TorchScript .pt 文件 (ImageNet-21k)
        String ptPath = modelDirectory + "/" + modelName + ".pt";
        if (tryLoadTorchScript(ptPath)) return;

        // 策略 1: 使用 PyTorch 引擎 + 显式 groupId/artifactId
        if (tryLoadFromZoo("PyTorch", "ai.djl.zoo", "resnet")) return;

        // 策略 2: 从 DJL 模型仓库 URL 下载并加载
        if (tryLoadFromRepoUrl()) return;

        // 策略 3: 从本地缓存目录加载
        if (tryLoadFromLocalCache()) return;

        // 策略 4: 尝试自动检测引擎
        if (tryLoadAutoDetect()) return;

        throw new ModelException("所有模型加载策略均失败，请检查网络连接或手动下载模型文件到 " + modelDirectory);
    }

    /**
     * 策略 1: 从 model zoo 加载
     * 使用 groupId + artifactId + filter 精确定位 ResNet 模型
     */
    private boolean tryLoadFromZoo(String engine, String groupId, String artifactId) {
        try {
            log.info("Trying: zoo [engine={}, group={}, artifact={}, layers=50]", engine, groupId, artifactId);
            Criteria<Image, Classifications> criteria = Criteria.builder()
                    .optGroupId(groupId)
                    .optArtifactId(artifactId)
                    .setTypes(Image.class, Classifications.class)
                    .optFilter("layers", "50")
                    .optEngine(engine)
                    .build();
            model = ModelZoo.loadModel(criteria);
            predictor = model.newPredictor();
            log.info("✓ Model loaded from zoo: {}/{} via {}", groupId, artifactId, engine);
            return true;
        } catch (Exception e) {
            log.warn("  ✗ Zoo load failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 策略 2: 从 DJL 模型仓库下载 ZIP 并加载
     */
    private boolean tryLoadFromRepoUrl() {
        try {
            String cacheDir = modelDirectory + "/resnet-50-cached";
            File cachePath = new File(cacheDir);

            // 如果缓存不存在，下载并解压
            if (!cachePath.exists() || !new File(cachePath, "resnet-50.param").exists()) {
                log.info("Downloading ResNet-50 from DJL repo: {}", MODEL_REPO_URL);
                cachePath.mkdirs();
                downloadAndExtractZip(MODEL_REPO_URL, cachePath);
            }

            log.info("Trying: load from local cache at {}", cachePath);
            Criteria<Image, Classifications> criteria = Criteria.builder()
                    .setTypes(Image.class, Classifications.class)
                    .optModelPath(cachePath.toPath())
                    .build();
            model = ModelZoo.loadModel(criteria);
            predictor = model.newPredictor();
            log.info("✓ Model loaded from cached repo download");
            return true;
        } catch (Exception e) {
            log.warn("  ✗ Repo URL load failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 策略 3: 从本地缓存目录加载
     */
    private boolean tryLoadFromLocalCache() {
        // 检查常见的缓存路径
        String[] cachePaths = {
                modelDirectory + "/resnet-50",
                modelDirectory + "/resnet50",
                modelDirectory
        };
        for (String path : cachePaths) {
            try {
                File dir = new File(path);
                if (dir.exists() && dir.isDirectory()) {
                    log.info("Trying: load from local path {}", path);
                    Criteria<Image, Classifications> criteria = Criteria.builder()
                            .setTypes(Image.class, Classifications.class)
                            .optModelPath(dir.toPath())
                            .build();
                    model = ModelZoo.loadModel(criteria);
                    predictor = model.newPredictor();
                    log.info("✓ Model loaded from local path: {}", path);
                    return true;
                }
            } catch (Exception e) {
                log.warn("  ✗ Local path {} failed: {}", path, e.getMessage());
            }
        }
        return false;
    }

    /**
     * 策略 4: 自动检测
     */
    private boolean tryLoadAutoDetect() {
        try {
            log.info("Trying: auto-detect engine from zoo");
            Criteria<Image, Classifications> criteria = Criteria.builder()
                    .optGroupId("ai.djl.zoo")
                    .optArtifactId("resnet")
                    .setTypes(Image.class, Classifications.class)
                    .optFilter("layers", "50")
                    .build();
            model = ModelZoo.loadModel(criteria);
            predictor = model.newPredictor();
            log.info("✓ Model loaded with auto-detected engine");
            return true;
        } catch (Exception e) {
            log.warn("  ✗ Auto-detect failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 下载并解压 ZIP 文件（DJL 模型仓库格式）
     */
    private void downloadAndExtractZip(String url, File targetDir) throws IOException {
        Path zipPath = targetDir.toPath().resolve("model.zip");
        // 下载
        log.info("Downloading {} ...", url);
        try (ReadableByteChannel in = Channels.newChannel(new URL(url).openStream());
             FileOutputStream out = new FileOutputStream(zipPath.toFile())) {
            out.getChannel().transferFrom(in, 0, Long.MAX_VALUE);
        }
        // 解压
        log.info("Extracting to {} ...", targetDir);
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new FileInputStream(zipPath.toFile()))) {
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.toPath().resolve(entry.getName());
                if (entry.isDirectory()) {
                    entryPath.toFile().mkdirs();
                } else {
                    entryPath.toFile().getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(entryPath.toFile())) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
        Files.deleteIfExists(zipPath);
        log.info("Download and extraction complete");
    }

    // ==================== 核心推理方法 ====================

    /**
     * 识别图片中的物体
     * @param imageStream 图片输入流
     * @return 识别结果列表（按置信度降序）
     * @throws IllegalStateException 模型未就绪时抛出
     */
    public List<RecognitionResult> recognize(InputStream imageStream) {
        if (!modelReady) {
            String msg = modelError != null ? modelError : "模型正在加载中，请稍后再试";
            throw new IllegalStateException(msg);
        }

        try {
            // 1. 读取图片
            Image img = ImageFactory.getInstance().fromInputStream(imageStream);

            // 2. 模型推理
            Classifications classifications = predictor.predict(img);

            // 3. 获取 top-K 结果
            List<Classifications.Classification> topKList = classifications.topK(topK);

            // 4. Softmax 归一化：模型可能输出原始 logits，需转为 0~1 概率
            //    检查第一个值，如果 >1 说明是 logits，需要 softmax
            boolean isRawLogits = !topKList.isEmpty() &&
                    (topKList.get(0).getProbability() > 1.0f ||
                     topKList.get(0).getProbability() < 0);

            if (isRawLogits) {
                // 数值稳定的 softmax：减去最大值防止指数溢出
                double maxLogit = Double.NEGATIVE_INFINITY;
                for (Classifications.Classification c : topKList) {
                    if (c.getProbability() > maxLogit) {
                        maxLogit = c.getProbability();
                    }
                }
                double sumExp = 0;
                double[] expValues = new double[topKList.size()];
                for (int i = 0; i < topKList.size(); i++) {
                    expValues[i] = Math.exp(topKList.get(i).getProbability() - maxLogit);
                    sumExp += expValues[i];
                }
                // 将 topK 结果替换为归一化后的概率
                List<RecognitionResult> results = buildResults(topKList, expValues, sumExp);
                results.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
                return results;
            } else {
                // 已经是概率，直接使用
                List<RecognitionResult> results = buildResults(topKList, null, 1.0);
                results.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
                return results;
            }

        } catch (TranslateException e) {
            log.error("Image recognition inference failed", e);
            throw new RuntimeException("图片识别推理失败: " + e.getMessage(), e);
        } catch (IOException e) {
            log.error("Failed to read image", e);
            throw new RuntimeException("图片读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 topK 分类结果构建 RecognitionResult 列表
     * @param topKList 模型输出的 topK 分类
     * @param expValues softmax 指数值（若为 null 则直接用原始概率）
     * @param sumExp   指数和（若 expValues 为 null 则传 1.0）
     */
    private List<RecognitionResult> buildResults(
            List<Classifications.Classification> topKList,
            double[] expValues, double sumExp) {

        List<RecognitionResult> results = new ArrayList<>();
        for (int i = 0; i < topKList.size(); i++) {
            Classifications.Classification c = topKList.get(i);

            // 计算归一化后的置信度
            float confidence;
            if (expValues != null) {
                confidence = (float) (expValues[i] / sumExp);
            } else {
                confidence = (float) c.getProbability();
            }

            if (confidence < confidenceThreshold) continue;

            String rawClassName = c.getClassName();
            String cleanedLabel = cleanLabel(rawClassName);
            String displayName = formatDisplayName(cleanedLabel);

            RecognitionResult result = new RecognitionResult();
            result.setLabel(rawClassName);
            result.setConfidence(confidence);
            result.setDisplayName(displayName);

            searchWordInDatabase(result, displayName);

            results.add(result);
        }
        return results;
    }

    // ==================== 标签处理 ====================

    /**
     * 清洗 DJL 返回的类名，去除 synset ID 等前缀
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
     * 匹配策略：精确匹配 → 分词匹配 → 模糊搜索
     */
    private void searchWordInDatabase(RecognitionResult result, String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            result.setMatched(false);
            return;
        }

        // 策略1: 精确匹配（case-insensitive）
        Optional<Word> exactMatch = wordRepository.findByContent(displayName);
        if (exactMatch.isPresent()) {
            fillMatchedWord(result, exactMatch.get());
            return;
        }

        // 策略2: 对标签中的每个单词独立搜索（如 "tree frog" → 搜索 "tree" 和 "frog"）
        String[] parts = displayName.split("\\s+");
        for (String part : parts) {
            if (part.length() < 3) continue;
            Optional<Word> partMatch = wordRepository.findByContent(part);
            if (partMatch.isPresent()) {
                fillMatchedWord(result, partMatch.get());
                return;
            }
        }

        // 策略3: 模糊搜索包含该标签的单词
        List<Word> fuzzyMatches = wordRepository.findByContentContainingIgnoreCase(displayName);
        if (!fuzzyMatches.isEmpty()) {
            fillMatchedWord(result, fuzzyMatches.get(0));
            return;
        }

        // 策略4: 分词模糊搜索
        for (String part : parts) {
            if (part.length() < 3) continue;
            List<Word> partFuzzyMatches = wordRepository.findByContentContainingIgnoreCase(part);
            if (!partFuzzyMatches.isEmpty()) {
                fillMatchedWord(result, partFuzzyMatches.get(0));
                return;
            }
        }

        // 未匹配
        result.setMatched(false);
    }

    private void fillMatchedWord(RecognitionResult result, Word word) {
        result.setMatched(true);
        result.setWordId(word.getWordId());
        result.setContent(word.getContent());
        result.setTranslation(word.getTranslation());
        result.setPhonetic(word.getPhonetic());
        result.setPartOfSpeech(word.getPartOfSpeech());
    }

    // ==================== 资源清理 ====================

    @PreDestroy
    public void cleanup() {
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
        log.info("OCR model resources released");
    }

    // ==================== 状态查询 ====================

    public boolean isModelReady() {
        return modelReady;
    }

    public String getModelError() {
        return modelError;
    }

    public int getValidLabelCount() {
        return validLabels.size();
    }

    // ==================== 内部类 - 识别结果 ====================

    /**
     * 单条识别结果，包含模型输出和数据库匹配信息
     */
    public static class RecognitionResult {
        /** 模型原始输出标签名（如 "n01644373 tree frog"） */
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
