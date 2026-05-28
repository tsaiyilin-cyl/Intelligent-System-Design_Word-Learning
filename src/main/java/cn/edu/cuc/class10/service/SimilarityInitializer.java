package cn.edu.cuc.class10.service;

import cn.edu.cuc.class10.entity.Word;
import cn.edu.cuc.class10.repository.WordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SimilarityInitializer {

    @Autowired
    private WordRepository wordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_SIMILAR_PER_WORD = 8;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialize() {
        List<Word> allWords = wordRepository.findAll();
        if (allWords.isEmpty()) return;

        boolean alreadyComputed = allWords.stream()
                .anyMatch(w -> w.getSimilarSpellings() != null && !w.getSimilarSpellings().isEmpty());
        if (alreadyComputed) return;

        long start = System.currentTimeMillis();

        Map<String, List<Map<String, Object>>> spellingsMap = computeSimilarSpellings(allWords);
        Map<String, List<Map<String, Object>>> meaningsMap = computeSimilarMeanings(allWords);

        int updated = 0;
        for (Word word : allWords) {
            boolean changed = false;

            List<Map<String, Object>> spellings = spellingsMap.get(word.getWordId());
            if (spellings != null && !spellings.isEmpty()) {
                try {
                    List<Map<String, Object>> top = spellings.size() > MAX_SIMILAR_PER_WORD
                            ? spellings.subList(0, MAX_SIMILAR_PER_WORD)
                            : spellings;
                    word.setSimilarSpellings(objectMapper.writeValueAsString(top));
                    changed = true;
                } catch (Exception ignored) {}
            }

            List<Map<String, Object>> meanings = meaningsMap.get(word.getWordId());
            if (meanings != null && !meanings.isEmpty()) {
                try {
                    List<Map<String, Object>> top = meanings.size() > MAX_SIMILAR_PER_WORD
                            ? meanings.subList(0, MAX_SIMILAR_PER_WORD)
                            : meanings;
                    word.setSimilarMeanings(objectMapper.writeValueAsString(top));
                    changed = true;
                } catch (Exception ignored) {}
            }

            if (changed) {
                wordRepository.save(word);
                updated++;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[SimilarityInitializer] done (" + elapsed + "ms, updated " + updated + " words)");
    }

    /**
     * Compute similar spellings (拼写相似) using Levenshtein distance <= 2.
     * Optimized: group by first letter + length filter to avoid O(n^2) over 10k words.
     */
    private Map<String, List<Map<String, Object>>> computeSimilarSpellings(List<Word> allWords) {
        Map<Character, List<Word>> byFirstLetter = allWords.stream()
                .filter(w -> w.getContent() != null && !w.getContent().isEmpty())
                .collect(Collectors.groupingBy(w -> Character.toLowerCase(w.getContent().charAt(0))));

        // Sort each group by content length for efficient comparison
        byFirstLetter.values().forEach(list ->
                list.sort(Comparator.comparingInt(w -> w.getContent().length())));

        Map<String, List<Map<String, Object>>> result = new HashMap<>();

        for (List<Word> group : byFirstLetter.values()) {
            for (int i = 0; i < group.size(); i++) {
                Word w1 = group.get(i);
                String c1 = w1.getContent().toLowerCase();
                int len1 = c1.length();

                for (int j = i + 1; j < group.size(); j++) {
                    Word w2 = group.get(j);
                    String c2 = w2.getContent().toLowerCase();
                    int len2 = c2.length();

                    if (Math.abs(len1 - len2) > 2) continue;

                    int distance = levenshteinDistance(c1, c2);
                    if (distance > 0 && distance <= 2) {
                        Map<String, Object> entry1 = new HashMap<>();
                        entry1.put("word_id", w2.getWordId());
                        entry1.put("edit_distance", distance);
                        result.computeIfAbsent(w1.getWordId(), k -> new ArrayList<>()).add(entry1);

                        Map<String, Object> entry2 = new HashMap<>();
                        entry2.put("word_id", w1.getWordId());
                        entry2.put("edit_distance", distance);
                        result.computeIfAbsent(w2.getWordId(), k -> new ArrayList<>()).add(entry2);
                    }
                }
            }
        }

        // Sort by edit_distance ascending for each word
        for (List<Map<String, Object>> list : result.values()) {
            list.sort(Comparator.comparingInt(m -> (int) m.get("edit_distance")));
        }

        return result;
    }

    /**
     * Compute similar meanings (语义相似) by:
     * 1. Same primary part of speech
     * 2. Chinese translation character overlap >= 30%
     */
    private Map<String, List<Map<String, Object>>> computeSimilarMeanings(List<Word> allWords) {
        // Filter to words with non-empty partOfSpeech
        Map<String, List<Word>> byPos = allWords.stream()
                .filter(w -> w.getPartOfSpeech() != null && !w.getPartOfSpeech().isEmpty())
                .collect(Collectors.groupingBy(w -> {
                    String pos = w.getPartOfSpeech();
                    return pos.contains("/") ? pos.split("/")[0].trim() : pos;
                }));

        Map<String, List<Map<String, Object>>> result = new HashMap<>();

        for (List<Word> group : byPos.values()) {
            if (group.size() < 2) continue;

            for (int i = 0; i < group.size(); i++) {
                Word w1 = group.get(i);
                String t1 = w1.getTranslation();

                for (int j = i + 1; j < group.size(); j++) {
                    Word w2 = group.get(j);
                    String t2 = w2.getTranslation();

                    double overlap = translationOverlap(t1, t2);
                    if (overlap >= 0.3) {
                        Map<String, Object> entry1 = new HashMap<>();
                        entry1.put("word_id", w2.getWordId());
                        entry1.put("similarity_score", overlap);
                        result.computeIfAbsent(w1.getWordId(), k -> new ArrayList<>()).add(entry1);

                        Map<String, Object> entry2 = new HashMap<>();
                        entry2.put("word_id", w1.getWordId());
                        entry2.put("similarity_score", overlap);
                        result.computeIfAbsent(w2.getWordId(), k -> new ArrayList<>()).add(entry2);
                    }
                }
            }
        }

        // Sort by similarity_score descending for each word
        for (List<Map<String, Object>> list : result.values()) {
            list.sort((a, b) -> Double.compare(
                    (double) b.get("similarity_score"),
                    (double) a.get("similarity_score")
            ));
        }

        return result;
    }

    /**
     * Compute character overlap ratio between two Chinese translation strings.
     * Removes common punctuation before comparison.
     */
    private double translationOverlap(String t1, String t2) {
        if (t1 == null || t2 == null || t1.isEmpty() || t2.isEmpty()) return 0;

        String clean1 = t1.replaceAll("[\\s\\p{Punct}\\p{IsPunctuation}]", "");
        String clean2 = t2.replaceAll("[\\s\\p{Punct}\\p{IsPunctuation}]", "");

        Set<Character> chars1 = clean1.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());
        Set<Character> chars2 = clean2.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());

        Set<Character> intersection = new HashSet<>(chars1);
        intersection.retainAll(chars2);

        Set<Character> union = new HashSet<>(chars1);
        union.addAll(chars2);

        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    /**
     * Standard Levenshtein edit distance.
     */
    private int levenshteinDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();

        int[] dp = new int[n + 1];
        for (int j = 0; j <= n; j++) dp[j] = j;

        for (int i = 1; i <= m; i++) {
            int prev = dp[0];
            dp[0] = i;
            for (int j = 1; j <= n; j++) {
                int temp = dp[j];
                dp[j] = Math.min(
                        prev + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1),
                        Math.min(dp[j] + 1, dp[j - 1] + 1)
                );
                prev = temp;
            }
        }

        return dp[n];
    }
}
