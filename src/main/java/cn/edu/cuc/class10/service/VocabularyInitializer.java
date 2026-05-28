package cn.edu.cuc.class10.service;

import cn.edu.cuc.class10.entity.Word;
import cn.edu.cuc.class10.entity.WordType;
import cn.edu.cuc.class10.repository.WordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

/**
 * 词汇表初始化服务
 * 应用启动时自动从 JSON 文件导入考纲词汇到数据库
 */
@Service
public class VocabularyInitializer {

    @Autowired
    private WordRepository wordRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        if (wordRepository.count() > 0) {
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            ClassPathResource resource = new ClassPathResource("vocabulary.json");
            InputStream inputStream = resource.getInputStream();

            JsonNode rootNode = mapper.readTree(inputStream);

            int count = 0;

            Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String phase = field.getKey();
                JsonNode wordsArray = field.getValue();

                for (JsonNode wordNode : wordsArray) {
                    String content = wordNode.get("content").asText();
                    // partOfSpeech 可能为空，使用空字符串作为默认值
                    String partOfSpeechStr = wordNode.has("partOfSpeech") && !wordNode.get("partOfSpeech").asText().isEmpty() 
                            ? wordNode.get("partOfSpeech").asText() 
                            : "";
                    String translation = wordNode.get("translation").asText();
                    String phonetic = wordNode.has("phonetic") && !wordNode.get("phonetic").asText().isEmpty() 
                            ? wordNode.get("phonetic").asText() 
                            : "";
                    String wordTypeStr = wordNode.get("wordType").asText();

                    if (!wordRepository.existsByContent(content)) {
                        try {
                            WordType wordType = WordType.valueOf(wordTypeStr);

                            // 创建单词对象
                            Word word = new Word(content, partOfSpeechStr, translation, phonetic, wordType, phase);

                            // 处理 phrases（短语）- 如果存在则转换为 JSON 字符串存储
                            if (wordNode.has("phrases") && wordNode.get("phrases").isArray() && wordNode.get("phrases").size() > 0) {
                                String phrasesJson = mapper.writeValueAsString(wordNode.get("phrases"));
                                word.setPhrases(phrasesJson);
                            } else {
                                word.setPhrases(null);
                            }

                            // 处理 sentences（句子）- 如果存在则转换为 JSON 字符串存储
                            if (wordNode.has("sentences") && wordNode.get("sentences").isArray() && wordNode.get("sentences").size() > 0) {
                                String sentencesJson = mapper.writeValueAsString(wordNode.get("sentences"));
                                word.setSentences(sentencesJson);
                            } else {
                                word.setSentences(null);
                            }

                            wordRepository.save(word);
                            count++;
                        } catch (IllegalArgumentException e) {
                            // 静默处理错误单词
                        }
                    }
                }
            }

            System.out.println("✅ 初始化完成，共导入 " + count + " 个单词");

        } catch (Exception e) {
            System.err.println("❌ 初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
