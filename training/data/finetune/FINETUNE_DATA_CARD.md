# Fine-tuning data (allergens)

Built 2026-10-30 by `FineTuneFileTest` from `training/data/allergen_train.jsonl`.
Teacher: `gpt-4.1` (answered as gpt-4.1-2025-04-14).
Rule (decisions log, 2026-10-30): keep a product only when the teacher's allergens equal Open Food Facts'.

| Step | Products |
|---|---|
| Training products with ingredients | 3000 |
| Skipped: only a "may contain" statement | 0 |
| No teacher answer (failed calls) | 2 |
| Dropped: teacher and OFF disagree (`disagreements.jsonl`) | 511 |
| **Kept** | **2487** (83.0% of answered) |
| of which with no allergen | 891 |
| `train.jsonl` | 2225 |
| `validation.jsonl` | 262 |

Kept examples per allergen:

| Allergen | Examples |
|---|---|
| en:celery | 23 |
| en:crustaceans | 5 |
| en:eggs | 200 |
| en:fish | 42 |
| en:gluten | 670 |
| en:lupin | 2 |
| en:milk | 1014 |
| en:molluscs | 2 |
| en:mustard | 56 |
| en:nuts | 238 |
| en:peanuts | 43 |
| en:sesame-seeds | 28 |
| en:soybeans | 518 |
| en:sulphur-dioxide-and-sulphites | 45 |

Size: about 384K tokens per epoch in train.jsonl (characters / 4).
Teacher calls this run: 16745 input, 1006 output tokens.

Known limits:
- Keeping only agreements drops the hard cases where OFF is right and the teacher wrong, so the
  fine-tuned model can't learn to beat its teacher on those.
- Where OFF and the teacher are wrong together, the error stays in the training data.
- The test set (`eval-food/data/off/allergen_test.jsonl`) never appears here: the split was made by
  `build_allergen_sets.py`, and duplicates were removed across both.
