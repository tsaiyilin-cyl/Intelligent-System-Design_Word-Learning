---
name: familiarity-isolation
description: Removed global Word.familiarity; all familiarity now per-user via UserWordFamiliarity table
metadata:
  type: project
---

- **Global `Word.familiarity` removed**: The `Word` entity no longer has a `familiarity` field. It was a global column shared by all users, causing data confusion.
- **All familiarity now per-user**: Study card, learning mode, report, and test system all read/write `UserWordFamiliarity` (keyed by userId + wordId).
- **Defaults**: Unstudied words default to 50 (in `UserWordFamiliarity` lookup).
- **Similarity groups**: `SimilarityInitializer` computes `similarMeanings` (semantic, POS + translation overlap) and `similarSpellings` (orthographic, Levenshtein ≤ 2) at startup. `TestService` uses them for distractor generation.

- **similarMeanings** (相似词义群): Computed by grouping words by part of speech, then filtering by Chinese translation character overlap >= 30%. Stored as JSON `[{word_id, similarity_score}]` in Word.similarMeanings.
- **similarSpellings** (相似词样群): Computed by grouping words by first letter, then Levenshtein distance <= 2 with length diff <= 2. Stored as JSON `[{word_id, edit_distance}]` in Word.similarSpellings.
- **How it's used**: `TestService.buildQuestion()` now uses similarSpellings translations as distractors for en2zh_choice, and similarMeanings content as distractors for zh2en_choice. Falls back to random if insufficient.
- **Max per word**: 8 entries per group.
- **Idempotent**: Skips computation if any word already has similarSpellings populated.

**Why:** The tech design specified these groups; previously distractors were the same 3 fixed words from findAll().limit(3) with no semantic relationship.

**How to apply:** When adding new words to the vocabulary, either restart the app to trigger re-computation, or call WordService.addSimilarMeaning/addSimilarSpelling manually.