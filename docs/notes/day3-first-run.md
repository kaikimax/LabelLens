# Day 3 — First real runs

**Date:** 2026-10-21 · **Model:** `gpt-4.1-mini-2025-04-14` (Azure, Global Standard) · **Prompt:** `prompts/read-label.md`, first version · **Temperature:** 0

Manual comparison of the AI's reading against my hand-typed answers in `labels.json`. Three products, four runs. Not a measurement: a first look, to decide what the day 5 eval must handle.

---

## Results

### p004 — Strawberry jam (French table, curved jar, hand in photo) · 1 photo

**All 8 nutrition values exact.**

| Field | Expected | AI |
|---|---|---|
| Energy | 1088.4 kJ | 1088.4 ✓ |
| Fat / saturated fat | 0.3 / 0.0 | 0.3 / 0 ✓ |
| Carbohydrates / sugars | 62.9 / 60.0 | 62.9 / 60 ✓ |
| Fibre | null | null ✓ |
| Protein / salt | 0.47 / 0.05 | 0.47 / 0.05 ✓ |
| Ingredients | `gélifiant: Pectine…` | `gélifiant : Pectine…` (space before colons) |
| Name / brand | Confiture de Fraises / EL BARAKA | مربى الفراولة / البركة |

- Name and brand are in Arabic because only the back photo was sent, where they're printed in Arabic. Correct for what the model saw; not scored.
- The ingredients only differ by spaces before colons. TODO: check the jar to see which version is printed.

**Cost:** 2,471 input tokens + 187 output tokens, about $0.001.

### p007 — Tabasco Sriracha (US-style table in Arabic, per 5 ml serving) · 1 photo

**Almost every number wrong.** Ingredients correct.

| Field | Expected (per 100 ml) | AI | What happened |
|---|---|---|---|
| Basis | serving, 5 ml | `100g` | Missed "حجم الحصة 5 مل" (serving size 5 ml) → no ×20 conversion |
| Energy | 418.4 kJ | 5 kJ | Read "5" but labelled it kJ instead of kcal (السعرات الحرارية = calories) |
| Carbohydrates | 40 | 1 | Wrong row (probably sugars) |
| Sugars | 20 | 0 | Shifted by one row |
| Salt | 8.5 | 0.17 | Reported sodium (170 mg) as salt → no ×2.5 conversion |
| Ingredients | ✓ | ✓ | |

**Root cause:** the model treated a US-style table (per serving, calories, sodium in mg), written in Arabic, as a European per-100 g table. None of the conversions built into `LabelConverter` were triggered, because the model never reported `serving`, `kcal` or `sodium`.

**Not caught by the sanity checks:** energy 5 kJ and macros of about 17 kJ are both under the 40 kJ threshold, so the energy check is skipped.

### p009 — Perly, fromage frais (blurry, small text) · same nutrition photo, two runs

| Field | Expected | Run 1 (nutrition photo only) | Run 2 (nutrition + ingredients photos) |
|---|---|---|---|
| Energy | 406.8 | 408.5 | 408 |
| Fat | 3.0 | **0.0** | **2.1** |
| Saturated fat | 2.1 | 2.1 | **null** |
| Carbohydrates / sugars | 9.4 / 6.5 | ✓ | ✓ |
| Protein / salt | 8.0 / 0.0 | ✓ | ✓ |
| Type | GENERAL | CHEESE | GENERAL |
| Brand | Jaouda | null | **"Paysan Breton"** |
| Ingredients | ✓ | null (photo not sent: correct) | ✓ |

- **Run 1:** fat misread as 0 → saturated fat (2.1) > fat (0) and the energy is 27% off → **both checks would warn** (rules c and d).
- **Run 2:** the saturated fat value moved into the fat field, and saturated fat became null → **no check can catch it** (rule c can't compare with null; energy only 8% off).
- **Run 1 left ingredients null** when the ingredients photo wasn't sent: the "never guess" rule worked.
- **Run 2's brand "Paysan Breton"** looks invented: Perly is a Jaouda product. TODO: check the pot. If it isn't printed, it's a hallucination despite the "never guess" rule.

---

## Findings

1. **Accuracy depends heavily on the label layout.** Perfect on a French per-100 g table (p004); mostly wrong on a US-style per-serving table in Arabic (p007). The eval must report results by situation (layout, language, photo quality), not only an overall score.

2. **The same photo gives different answers on different runs**, even at temperature 0 (p009: fat 0.0, then 2.1). Part of the difference comes from adding a second photo, but models also vary on identical inputs. **One run per product is not a reliable measurement:** the eval should run each product several times and report how stable the answers are.

3. **Sanity checks catch some misreads, not all.** They would have caught p009 run 1, but not p009 run 2 or p007. They're a safety net, not a measurement.

4. **The model may invent text** (p009 brand, to confirm). The brand isn't scored, but the same behaviour on allergens would be dangerous, so it's worth testing explicitly on day 6.

5. **Exact text comparison is too strict for ingredients.** A one-space difference (p004) would count a correct reading as wrong. The eval needs a comparison that ignores spacing and punctuation but not words.

6. **Product type is ambiguous for fresh cheeses** (p009 read as CHEESE once, GENERAL once). This depends on how the 2023 Nutri-Score classifies "fromage frais" / "fromage blanc", to settle on day 4 (also affects p005).

---

## Ideas to test once the eval exists (day 5)

Not applied yet: without a measurement, there's no way to know whether a change helps one product and breaks another.

1. **Prompt: US-style tables.** Explain per-serving tables and give the Arabic words for serving size (حجم الحصة), calories (السعرات الحرارية) and sodium (الصوديوم). Target: p007.
2. **Prompt: row alignment.** Ask the model to read each row from its label to its value, especially in tables with Arabic labels on the right and numbers on the left. Target: p007, p009.
3. **Brand: only from the photo.** Make explicit that name and brand must be read from the photos, never from knowledge of the product. Target: p009.
4. **Several runs per product** in the eval, reporting the share of runs where each field is correct.

---

## Not tested yet

- p003 (mayonnaise, ingredients in Arabic) and p011 (soy sauce, kcal/kJ case). Left for the day 5 eval run, which covers every product.