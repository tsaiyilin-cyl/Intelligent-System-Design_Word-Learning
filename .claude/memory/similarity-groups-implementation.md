---
name: familiarity-fix
description: Fixed dual familiarity system - study card updates now sync to UserWordFamiliarity, and default changed from 0 to 50
metadata:
  type: project
---

- **Dual familiarity systems**: There were two separate stores for word familiarity — `Word.familiarity` (used by study card UI) and `UserWordFamiliarity.familiarity` (used by report/test system). The study card was only updating `Word.familiarity`, causing the report to show 0 mastered words.
- **Fix 1**: `WordService.updateWordFamiliarity(wordId, newFamiliarity, userId)` now syncs to both stores (words table + user_word_familiarity table).
- **Fix 2**: `Word` constructors now default `familiarity = 50` instead of 0, so unstudied words show 50% instead of 0%.
- **Fix 3**: `WordService.migrateDefaultFamiliarity()` runs at startup via `@PostConstruct` to migrate existing words from 0→50.
- **Boundary checks**: `InteractionService.recordInteraction()` uses `Math.max(0, Math.min(100, oldF + delta))` ✓ and `WordService.updateFamiliarity()` validates 0-100 ✓ — both already correct.

- **similarMeanings** (相似词义群): Computed by grouping words by part of speech, then filtering by Chinese translation character overlap >= 30%. Stored as JSON `[{word_id, similarity_score}]` in Word.similarMeanings.
- **similarSpellings** (相似词样群): Computed by grouping words by first letter, then Levenshtein distance <= 2 with length diff <= 2. Stored as JSON `[{word_id, edit_distance}]` in Word.similarSpellings.
- **How it's used**: `TestService.buildQuestion()` now uses similarSpellings translations as distractors for en2zh_choice, and similarMeanings content as distractors for zh2en_choice. Falls back to random if insufficient.
- **Max per word**: 8 entries per group.
- **Idempotent**: Skips computation if any word already has similarSpellings populated.

**Why:** The tech design specified these groups; previously distractors were the same 3 fixed words from findAll().limit(3) with no semantic relationship.

**How to apply:** When adding new words to the vocabulary, either restart the app to trigger re-computation, or call WordService.addSimilarMeaning/addSimilarSpelling manually.