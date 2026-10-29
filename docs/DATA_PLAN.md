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

### Where the files go

```
eval-food/
  data/
    photos/
      labels.json          ← the correct answers, one entry per product
      images/
        p001_nutrition.jpg
        p001_ingredients.jpg
        p001_front.jpg
        p002_nutrition.jpg
        ...
```

### Typing the correct answers
For each product, add one entry to the `products` list in `labels.json`. About 2–3 minutes per product. The template file has one example entry: replace it with your first real product.

```json
{
  "version": 1,
  "nutriscore_version": "2023",
  "products": [
    {
      "id": "p001",
      "photos": {
        "nutrition": "p001_nutrition.jpg",
        "ingredients": "p001_ingredients.jpg",
        "front": "p001_front.jpg"
      },
      "situations": ["arabic", "round-packaging", "blurry"],
      "barcode": "6111234567890",
      "name": "Biscuits au chocolat",
      "brand": "Example Brand",
      "type": "GENERAL",
      "per_100g": {
        "energy_kj": 1980,
        "fat": 19.0,
        "saturated_fat": 6.5,
        "carbohydrates": 68.0,
        "sugars": 28.0,
        "fibre": 2.1,
        "protein": 6.8,
        "salt": 0.45
      },
      "ingredients_text": "Farine de blé, sucre, huile de palme, ...",
      "ingredients_language": "fr",
      "allergens": ["en:gluten", "en:milk", "en:soybeans"],
      "may_contain": ["en:nuts"],
      "printed_grade": null,
      "notes": ""
    }
  ]
}
```

### Field by field

| Field | What to write | Allowed values |
|---|---|---|
| `id` | `p001`, `p002`… Gaps are fine (`p001`, `p004`, `p008`): the id only has to be unique. Never reuse a number, even if you delete a product. | |
| `photos` | The file names in `images/`. `front` is `null` if you didn't take one. | |
| `situations` | Everything that makes this product harder or special. Used to split the scores. | `arabic`, `french-only`, `round-packaging`, `shiny-packaging`, `flat-packaging`, `blurry`, `glare`, `angled`, `hand-in-photo`, `small-text`, `per-portion-converted`, `printed-as-less-than` |
| `barcode` | The digits under the barcode, as text (keep leading zeros). | |
| `name`, `brand` | As printed on the front, in whatever language is printed (Arabic is fine). Not scored; only there so you recognise the product. Save the file as UTF-8. | |
| `type` | Which Nutri-Score rules apply. `GENERAL`: normal food (biscuits, cereals, yogurt, jam, pasta, sauces, snacks), **including fromage blanc and fromage frais**. `DRINK`: anything you drink, milk included. `CHEESE`: ripened cheeses only. `OIL`: oils, butter, margarine, nuts, seeds, and **nut or seed butters (peanut butter)**. Same rules as Open Food Facts, which Test 2a checks against (see PLAN.md, decisions log). | `GENERAL`, `DRINK`, `CHEESE`, `OIL` |
| `per_100g` | The eight values, as printed, per 100 g (or 100 ml for drinks): energy, fat, saturated fat, carbohydrates, sugars, fibre, protein, salt. **Energy in kJ**, not kcal. If a value isn't printed at all, write `null`, not `0`. `fat` and `carbohydrates` aren't used by Nutri-Score, but the app's sanity checks need them (see PLAN.md, day 3). | numbers or `null` |
| `ingredients_text` | The full ingredients list, copied exactly, in the main language on the package. Keep percentages and punctuation. **Leave out the heading** ("Ingrédients :"): start at the first ingredient. | |
| `ingredients_language` | The language you copied. | `fr`, `ar`, `en` |
| `allergens` | Allergens **you find by reading the ingredients list**. Labels rarely print an "allergens" section, so this is your judgment, using the table below. Empty list `[]` if there are none. | the 14 tags from Part 2 |
| `may_contain` | Allergens in "may contain traces of…". Not scored in v1. | the 14 tags from Part 2 |
| `printed_grade` | The Nutri-Score **only if it's printed on the package** (rare in Morocco; mostly imported European products). Otherwise `null`. Don't calculate it yourself: the correct grade is computed on day 4 from your typed values, with the Java formula that Test 2a has checked against Open Food Facts. | `A`–`E` or `null` |
| `notes` | Anything odd, e.g. "salt printed twice with different values". | |

### Finding allergens in the ingredients

| Allergen tag | Words to look for (French) | Arabic |
|---|---|---|
| `en:gluten` | blé, farine (de blé), orge, seigle, avoine, épeautre, gluten, malt | قمح، شعير |
| `en:milk` | lait, lactose, lactosérum, beurre, crème, fromage, caséine, protéines de lait | حليب |
| `en:eggs` | œuf, œufs, jaune d'œuf | بيض |
| `en:soybeans` | soja, lécithine de soja | صويا |
| `en:peanuts` | arachide, cacahuète | فول سوداني |
| `en:nuts` | amande, noisette, noix, pistache, cajou | لوز، بندق |
| `en:sesame-seeds` | sésame | سمسم |
| `en:mustard` | moutarde | خردل |
| `en:sulphur-dioxide-and-sulphites` | sulfites, anhydride sulfureux, métabisulfite, E220 to E228 | |
| `en:fish`, `en:crustaceans`, `en:molluscs`, `en:celery`, `en:lupin` | poisson, crevette / crustacés, mollusques, céleri, lupin | |

- "Lactose-free milk" (sans lactose) is still `en:milk`.
- **Soybean oil does not count** as `en:soybeans`: fully refined oils are exempt under EU rules. Soy itself, soy sauce and soy lecithin do count.
- Sulphites hide in other ingredients: "métabisulfite de potassium" inside a mustard still makes the product `en:sulphur-dioxide-and-sulphites`.
- **Check every product with `python eval-food/validate_labels.py`**: it flags format errors and ingredients that mention an allergen word you didn't list.
- Wheat flour in any form counts, including "amidon de blé" (wheat starch).
- "Peut contenir des traces de…" / "may contain" goes in `may_contain`, not `allergens`.
- If you're unsure about an ingredient, leave it out and write it in `notes`. Never guess.

### Tricky cases
- **"< 0.5 g"**: write `0.5`, and add `printed-as-less-than` to `situations`.
- **Only "per portion" printed**: convert to per 100 g (value ÷ portion weight × 100), and add `per-portion-converted`.
- **Only kcal printed**: kJ = kcal × 4.184.
- **Sodium instead of salt** (common on US-style labels): salt = sodium × 2.5, in grams (170 mg = 0.17 g).
- **US-style tables round heavily** ("2 g" can mean 1.5 to 2.4 g; "0 g" means under 0.5 g). Convert the printed numbers anyway, and note the rounding in `notes`. The app converts the same printed numbers the same way, so the comparison stays fair; only the energy sanity check may warn, which is correct.
- **French and Arabic both printed**: copy the French version into `ingredients_text` (most labels have it), and add `arabic` to `situations` so you can still split scores by labels with Arabic on them.
- **`null` vs `0`**: `0` means "printed as 0 g." `null` means "not printed." The difference matters: the earlier Open Food Facts check showed fake zeros causing errors, and you don't want to create the same problem in your own data.
- **Always write the barcode.** It's how you make sure these products never end up in the training data.

**Do 5 products first, then check the format with me before doing the other 35.**

---

## Part 2 — Open Food Facts data

### What it is
Open Food Facts is a free database of over 3 million food products, filled in by volunteers (like Wikipedia, for food). Each product has its ingredients, nutrition values, allergens and Nutri-Score. Free to use, as long as you credit it.

### Getting it
- Download the full product file from the Open Food Facts website (the Parquet format is the smallest; it's also on Hugging Face).
- It's big. Never put the full file on GitHub. Only put the small filtered files you create.

### What's in the file (checked on 2026-10-12 with `training/check_data.py`)

| Count | Products |
|---|---|
| Total | 4,792,125 |
| No data-quality errors | 4,609,648 |
| With an ingredients text (20+ characters) | 1,146,573 |
| … of which sold in France | 325,830 |
| … of which sold in Morocco | 5,931 |
| … of which have no allergens at all | 429,686 (37%) |
| Usable for the Nutri-Score check (grade a–e, values per 100 g, all six nutrients present) | 90,726 |

**Findings that affect the plan:**

1. **Nutri-Score version: 2023.** `misc_tags` contains tags like `en:nutriscore-2021-different-from-2023`, which compare the old 2021 formula against the current one. So the stored grade uses the **2023 formula**, and that's the one to code in Java. Test 2a confirms it: if the grades match almost 100%, it's the right version.

2. **Open Food Facts "allergens" include non-allergens.** `en:pork`, `en:gelatin` and `en:beef` appear in the top 15, but they're dietary flags, not legal allergens. **Keep only the 14 EU allergens** (list below) in training and testing, and drop all other tags.

3. **Fruit and vegetable percentage isn't printed on labels.** The 2023 formula uses the percentage of fruit, vegetables and legumes (`fruits-vegetables-legumes` in `nutriments`). Open Food Facts estimates it from the ingredients, but your photos won't show it. So:
   - in Test 2a (formula check), use Open Food Facts' value, to test the formula alone;
   - in the app and Test 2b, assume 0% when unknown, and count how often that changes the grade. That's a real limitation, so write it in the README.

4. **Some values are fake zeros.** The example product has 0.0 for every vitamin and mineral, which almost certainly means "not filled in" rather than "zero." The same can happen to sugars or saturated fat. Expect some Test 2a mismatches to come from this, not from your code. Check a few mismatches by hand before calling them bugs.

5. **The data has junk in it.** `nova-group` appears inside `nutriments` with the value 14.28, which is meaningless. Always read nutrients by exact name, and ignore everything else in the list.

6. **Salt and sodium are both there** (salt = sodium × 2.5). Use `salt`.

### The 14 EU allergens, as Open Food Facts tags them

| Allergen | Tag | Examples in the file |
|---|---|---|
| Milk | `en:milk` | 372,090 |
| Gluten | `en:gluten` | 307,053 |
| Soy | `en:soybeans` | 215,108 |
| Eggs | `en:eggs` | 121,644 |
| Tree nuts | `en:nuts` | 91,086 |
| Mustard | `en:mustard` | 41,561 |
| Fish | `en:fish` | 36,340 |
| Peanuts | `en:peanuts` | 36,172 |
| Celery | `en:celery` | 34,331 |
| Sesame | `en:sesame-seeds` | 32,064 |
| Sulphites | `en:sulphur-dioxide-and-sulphites` | 22,669 |
| Crustaceans | `en:crustaceans` | 11,082 |
| Molluscs | `en:molluscs` | under 10,000 (not in top 15) |
| Lupin | `en:lupin` | under 10,000 (not in top 15) |

Use these exact tags everywhere: training data, test data, `labels.json`, and the app's output. One spelling means no translation bugs.

Molluscs and lupin are rare, and they'll be even rarer among Moroccan products. If the test set has fewer than 10 products with one of them, report "too few examples to measure" for that allergen instead of a misleading percentage.

### Which columns to keep

| Column | Shape in the file | Why |
|---|---|---|
| `code` | text | The barcode, used to split products into groups |
| `lang` | text, e.g. `fr` | The product's main language |
| `product_name` | list of `{lang, text}` | To understand the data |
| `countries_tags` | list, e.g. `["en:morocco"]` | To find Moroccan and French products |
| `categories_tags` | list | To spot drinks, cheese and oils |
| `ingredients_text` | list of `{lang, text}` | The input for allergen finding. `lang` is `main` for the product's main language, or a code like `fr` or `ar`. |
| `allergens_tags` | list, e.g. `["en:milk", "en:gluten"]` | The correct allergens. Remove the `en:` prefix to get simple names. |
| `traces_tags` | list | "May contain" allergens. Recorded, not scored in v1. |
| `nutriments` | list of `{name, 100g, unit, …}` | Nutrition values. **Not separate columns:** search the list by `name` (`energy-kj`, `sugars`, `saturated-fat`, `salt`, `fiber`, `proteins`) and read the `100g` field. |
| `nutrition_data_per` | `100g` or `serving` | Skip products where values are only per serving. |
| `nutriscore_grade`, `nutriscore_score` | `a`–`e`, `unknown`, `not-applicable` | The correct grade and points total |
| `misc_tags` | list | Probably says which Nutri-Score version was used (see below) |
| `states_tags` | list | Shows whether ingredients were marked complete |
| `obsolete` | true/false | Skip products no longer sold |
| `data_quality_errors_tags` | list | Skip products with known errors |

**Check first:** the Nutri-Score formula was updated in 2023, and no column names the version directly. Look for tags containing `nutriscore` in `misc_tags`, and code the same version the grades use.

### Keeping only good products
Keep a product only if:
- `obsolete` is false,
- it has an ingredients text of at least 20 characters,
- `data_quality_errors_tags` is empty,
- for the Nutri-Score check: the grade is `a`–`e`, all six nutrition values exist in `nutriments` with a `100g` value, and `nutrition_data_per` is `100g`.

**Why the ingredients rule matters most:** an empty `allergens_tags` list `[]` can mean "no allergens" or "nobody typed the ingredients, so nothing was detected." Training on the second kind would teach the model to answer "no allergens" too often, which is the dangerous mistake. Requiring an ingredients text makes `[]` actually mean "none found."

Prefer Moroccan products, then French ones, so the text looks like the labels you'll photograph.

### Splitting into groups

| Group | Size | Taken from | Used for |
|---|---|---|---|
| Formula check | 5,000 | The 90,726 usable products, any country | Test 2a. No AI involved. |
| Testing | 500 | **Moroccan products only** (5,931 available) | Test 3 (the "exam"). Never used for training. |
| Training | 3,000 | Remaining Moroccan products first, topped up with French ones | Fine-tuning (the "practice questions") |

Why the test set is Moroccan: your app is aimed at Moroccan labels, so the score that matters is the score on Moroccan products. Training on mostly Moroccan products too means the model practises on the same kind of text it's tested on.

How to split without mistakes:
- Decide the group from the barcode (for example, the last digit decides). Re-running the script always gives the same groups.
- Remove from all groups any barcode among your 40 photographed products.
- If two products have exactly the same ingredients text, keep only one. Otherwise, the exam could contain a copy of a practice question under a different barcode.

### Training file format
Azure wants one line per example, like this:

```json
{"messages": [
  {"role": "system", "content": "List the EU allergens in this ingredients text. Answer in JSON: {\"allergens\": [...]} using only these tags: en:milk, en:gluten, en:soybeans, en:eggs, en:nuts, en:mustard, en:fish, en:peanuts, en:celery, en:sesame-seeds, en:sulphur-dioxide-and-sulphites, en:crustaceans, en:molluscs, en:lupin. If there are none, answer {\"allergens\": []}."},
  {"role": "user", "content": "Farine de blé, sucre, lait écrémé en poudre, lécithine de soja"},
  {"role": "assistant", "content": "{\"allergens\": [\"en:gluten\", \"en:milk\", \"en:soybeans\"]}"}
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
- [x] Download the Open Food Facts file and look at the columns.
- [x] Find out which Nutri-Score version Open Food Facts uses (2023).
