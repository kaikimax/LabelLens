# LabelLens — Data Plan

The project uses two sources of data:

| Source | How many | Used for |
|---|---|---|
| **My own photos** | 40 products | Testing the AI on real photos. **Never used for training.** I trust these most, because I check every value myself. |
| **Open Food Facts** | ~5,000 + ~3,500 products | Checking the Nutri-Score formula, training the allergen model, and testing it. |

---

## Part 1 — Photographing products

### Which products
40 products, mixed so the results say something useful:

| Mix | Target |
|---|---|
| Language | At least 15 with Arabic on the label, at least 15 French only |
| Packaging | At least 10 round (bottles, cans, yogurt pots), at least 10 shiny plastic, the rest flat cardboard |
| Type | At least 30 normal foods (biscuits, cereals, jam, yogurt, snacks, pasta, sauces); up to 10 drinks, cheese or oil, to test the "not scored" case |
| Allergens | At least 10 with milk, 10 with gluten, 3 with nuts or peanuts, 5 with no allergens at all |

Where to find them: your kitchen, family, one supermarket trip.

### How to photograph
- 2 photos per product: the nutrition table, and the ingredients list. A third photo of the front is optional.
- Take them like a normal person would, with your phone, no special effort.
- For 10 of the 40, **keep the bad photos** (a bit blurry, some glare, at an angle). The test should include real-life photos, not only perfect ones.
- File names: `p001_nutrition.jpg`, `p001_ingredients.jpg`, `p002_nutrition.jpg`…
- Before putting photos on GitHub, remove the hidden location data from them: `exiftool -all= *.jpg`.

### Typing the correct answers
For each product, type what's printed on the package into `eval-food/data/photos/labels.json`. About 2–3 minutes per product.

```json
{
  "id": "p001",
  "situations": ["arabic", "round-packaging", "blurry"],
  "barcode": "6111234567890",
  "name": "Biscuits au chocolat",
  "type": "GENERAL",
  "per_100g": {
    "energy_kj": 1980,
    "sugars": 28.0,
    "saturated_fat": 6.5,
    "salt": 0.45,
    "fibre": 2.1,
    "protein": 6.8
  },
  "ingredients_text": "Farine de blé, sucre, huile de palme, ...",
  "allergens": ["gluten", "milk"],
  "may_contain": ["nuts"],
  "correct_grade": "D",
  "grade_source": "printed on package"
}
```

Rules:
- Copy numbers **exactly as printed**, per 100 g. If the package only gives values "per portion", convert them and add `"per-portion-converted"` to `situations`.
- `type`: `GENERAL`, `DRINK`, `CHEESE` or `OIL`.
- `allergens`: only what the ingredients list contains. "May contain traces of…" goes in `may_contain`, which isn't scored in v1.
- `correct_grade`: if the grade is printed on the package, use it. Otherwise, calculate it once with an online Nutri-Score calculator and write which one in `grade_source`.
- Always write the barcode. It's how you make sure these products never end up in the training data.

**Do 5 products first, then check the format with me before doing the other 35.**

---

## Part 2 — Open Food Facts data

### What it is
Open Food Facts is a free database of over 3 million food products, filled in by volunteers (like Wikipedia, for food). Each product has its ingredients, nutrition values, allergens and Nutri-Score. Free to use, as long as you credit it.

### Getting it
- Download the full product file from the Open Food Facts website (the Parquet format is the smallest; it's also on Hugging Face).
- It's big. Never put the full file on GitHub. Only put the small filtered files you create.

### Which columns to keep

| Column | Why |
|---|---|
| `code` | The barcode, used to split products into groups |
| `product_name`, `countries_tags` | To filter and to understand the data |
| `categories_tags` | To spot drinks, cheese and oils |
| `ingredients_text` (and `_fr`, `_ar` versions) | The input for allergen finding |
| `allergens_tags` | The correct allergens |
| Nutrition columns: energy, sugars, saturated fat, salt, fibre, proteins (per 100 g) | Input for the Nutri-Score check |
| `nutriscore_grade` | The correct grade |

**Check first:** the Nutri-Score formula was updated in 2023. Find out which version the Open Food Facts grade uses, and code that same version.

### Keeping only good products
Keep a product only if:
- the ingredients text is at least 20 characters long,
- for the Nutri-Score check: all six nutrition values and a grade are filled in.

Prefer French and Moroccan products, so the text looks like the labels you'll photograph.

### Splitting into groups

| Group | Size | Used for |
|---|---|---|
| Formula check | ~5,000 | Test 2a. No AI involved. |
| Training | ~3,000 | Fine-tuning (the "practice questions") |
| Testing | 500 | Test 3 (the "exam"). Never used for training. |

How to split without mistakes:
- Decide the group from the barcode (for example, the last digit decides). Re-running the script always gives the same groups.
- Remove from all groups any barcode among your 40 photographed products.
- If two products have exactly the same ingredients text, keep only one. Otherwise, the exam could contain a copy of a practice question under a different barcode.

### Training file format
Azure wants one line per example, like this:

```json
{"messages": [
  {"role": "system", "content": "List the allergens in this ingredients text. Answer in JSON: {\"allergens\": [...]}"},
  {"role": "user", "content": "Farine de blé, sucre, lait écrémé en poudre, lécithine de soja"},
  {"role": "assistant", "content": "{\"allergens\": [\"gluten\", \"milk\", \"soy\"]}"}
]}
```

Each line says: "here's the instruction, here's an ingredients text, here's the correct answer." The model learns from 3,000 of these.

**Use exactly the same instruction** (the `system` line) when training and when testing the non-fine-tuned models. Otherwise the comparison isn't fair.

---

## Part 3 — A short note for each data file

Next to each data file you create, add a small `DATA_CARD.md`:
- where the data comes from and the download date,
- what you filtered out, and how many products were left after each step,
- how you split the groups,
- known problems (for example: "some allergen tags in Open Food Facts were added automatically, not by people"),
- the licence (Open Food Facts: ODbL, credit required).

10 minutes per file, and it shows you take data seriously.

---

## Day 1 checklist

- [ ] Photograph 20 products (2 photos each).
- [ ] Type in 5 of them in `labels.json` and send me one to check.
- [ ] Download the Open Food Facts file and look at the columns.
- [ ] Find out which Nutri-Score version Open Food Facts uses.