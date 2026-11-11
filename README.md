# LabelLens

[![CI](https://github.com/kaikimax/LabelLens/actions/workflows/ci.yml/badge.svg)](https://github.com/kaikimax/LabelLens/actions/workflows/ci.yml)

Photograph a food label (French, Arabic or English) → get its nutrition values, its **Nutri-Score**
and its **allergens**. The real subject of the project is the second half: **automatic tests that
measure how often the AI is right**, and what each AI step costs.

**Live:** https://labellens.braveocean-2b3eef8c.spaincentral.azurecontainerapps.io
(the first visit after a pause takes about 30 s: the app scales to zero when nobody uses it;
it accepts 200 uploads a day, because each one is paid from my Azure credit)

**Demo video (1:55):** https://youtube.com/shorts/sboEEKSa1qo

Java 21 · Spring Boot 4 · Spring AI · PostgreSQL · Azure OpenAI · Thymeleaf + htmx · Docker ·
GitHub Actions · Azure Container Apps

---

## Results at a glance

| What | Test set | Result |
|---|---|---|
| Reading nutrition values from photos | 31 products I photographed, 3 runs each | **87–100% per value** (sugars lowest, fibre highest) |
| Grade from photo to Nutri-Score | the 19 of them that can be graded | **77.2%** right grade |
| Nutri-Score formula (Java) | 5,000 Open Food Facts products | **99.12%** same grade; 99.82% once a known data issue is set aside |
| Allergens: word list + gpt-4.1 | 500 French ingredient texts | **99.5%** of allergens caught, 97.7% of alarms correct |
| Fine-tuned gpt-4.1-nano | same 500 | matches gpt-4.1 in about a quarter of the time per call; **not worth hosting** for this app |
| Cost and time | real uploads | **≈ $2 per 1,000 products, 7.5 s** of AI time per product |

Every number has a caveat, listed under [Read this before quoting a number](#read-this-before-quoting-a-number).
The full write-ups are in [`docs/notes/`](docs/notes) and every decision is in the
[decisions log](docs/PLAN.md).

---

## The design rule: AI only where code can't do the job

| Step | AI or code | Why |
|---|---|---|
| Reading the photo | **AI** (gpt-4.1-mini) | Curved, shiny, blurry packaging in three scripts: code can't read it reliably. |
| Sanity checks | Code | "Sugars can't exceed carbohydrates." Always right, easy to test. |
| Nutri-Score | Code | A published formula. A model would make arithmetic mistakes and vary between runs. |
| Allergens in the ingredients | **Code + AI** | A Java word list (free, predictable) **and** gpt-4.1, combined. Each catches what the other misses. |
| "May contain" allergens | Code | Only from the printed "may contain" sentence, with the word list. Never guessed. |

```
Browser ──▶ Spring Boot app
              ├─ reads the label ─────── AI call ─▶ Azure OpenAI (gpt-4.1-mini, reads images)
              ├─ sanity checks + Nutri-Score        (plain Java)
              ├─ finds allergens ─ word list + AI ─▶ Azure OpenAI (gpt-4.1)
              └─ records tokens, cost and time of every AI call
                                │
                                ▼
                     PostgreSQL (Azure Flexible Server)
```

The result page shows **every Nutri-Score point**, so the user sees *why* a product got a D, and
it never shows "no allergens" unless they were actually checked.

---

## Testing the AI

### The eval code knows nothing about food

[`eval-core/`](eval-core) runs any AI step on cases with known answers and scores it. It has no
food code in it. To test another AI system (receipts, invoices…) you write three small pieces,
not a new test framework:

```java
Subject<Photo, Receipt>  subject = photo -> /* call your model */ Attempt.answered(receipt, ms, in, out, retries);
Scorer<Receipt, Receipt> scorer  = (expected, answer) -> List.of(
        Check.of("total", Math.abs(expected.total() - answer.total()) < 0.01, expected.total(), answer.total()));
EvalResult result = new EvalRunner(System.out::println).run("receipts", cases, subject, scorer, 3);
```

It then gives you: scores per check and per situation tag ("arabic", "blurry"…), **stability**
(does the same input give the same answer in every run?), every disagreement to review, failed
calls, cost, and a comparison with the saved baseline that **fails the test when a score drops by
more than 3 points**.

### Test 1 and 2b: reading the photo (31 products × 3 runs)

Products photographed in Moroccan shops; the answer key typed by hand from each package.

| Check | Score | Check | Score |
|---|---|---|---|
| energy | 96.8% | fibre | 100% |
| fat | 96.8% | protein | 89.2% |
| saturated fat | 96.8% | salt | 96.8% |
| carbohydrates | 93.5% | type (general, drink…) | 95.7% |
| sugars | 87.1% | ingredients text, exact | 72.0% |
| **grade, photo → Nutri-Score** | **77.2%** | stability | 96.4% |

Every remaining disagreement was checked against the photo: all are reader errors, not key errors.
The patterns:

- **Arabic labels are the weak point:** table rows shifted (sugars read as protein), and Arabic
  ingredient lists rewritten or partly translated. An early prompt even made the model translate an
  Arabic-only list into French and loop until the 60 s timeout; it now has a "never translate" rule
  and a 2,000-token cap, so a runaway answer fails in seconds.
- **It ignores the language order** on 2 products (English list chosen over the French one).
- **One wrong value changes the grade:** 3 products (p003, p009, p026) cost 7 grade points between
  two prompt versions, although the change only touched the ingredients rules.

### Test 2a: the Nutri-Score formula (5,000 products)

The Java calculator (2023 rules, general foods) gives the same grade as Open Food Facts for
**4,956 / 5,000 (99.12%)**. 35 of the 44 differences are explained by OFF's fruit/vegetable data
(its export doesn't always hold the value it used); the other 9 are red meat and coconut products,
which follow special rules out of scope for v1. Each fix during the test was a fix to the **data
filter**, never to the formula. Runs on every push.

### Test 3: allergens (500 French ingredient texts from Open Food Facts)

| Setup | Caught | Correct alarms | Whole product right | Time per call |
|---|---|---|---|---|
| word list (Java, free) | 96.7% | 98.6% | 94.4% | 0 |
| gpt-4.1-nano | 91.8% | 88.1% | 83.8% | |
| gpt-4.1 | 97.0% | 97.5% | 93.8% | 2.3 s |
| **word list + gpt-4.1** (the app) | **99.5%** | **97.7%** | **96.8%** | 2.3 s |
| word list + fine-tuned nano | 99.5% | 97.9% | 97.0% | 0.5 s |

The main score is **caught** (recall): a missed allergen is dangerous, a false alarm only annoying.
On 227 Arabic-only texts the app's setup catches **96.3%**; precision isn't quotable there (see
below).

### The fine-tuning experiment

**Question:** is it worth fine-tuning a small, cheap model for allergens, or is a big model with
good instructions enough?

- **Data:** 3,000 Open Food Facts products. gpt-4.1 labelled each one, and a product was kept only
  when gpt-4.1 and Open Food Facts **agreed** (2,487 kept): day 6 had shown the OFF tags are the
  wrong side in about 3 of 4 disagreements. The 500 test products were never in the training data
  (split by barcode).
- **Result:** fine-tuning took nano from 83.8% to 94.8% of products fully right, and from 77 false
  alarms (combined with the word list) to 14, gpt-4.1's level, at 0.5 s per call instead of 2.3 s.
  It also helped on Arabic (88.3% → 93.2% caught) although it was trained on French text only.
  Training cost about $1.75.
- **Verdict: not for this app.** A fine-tuned model costs about **$1.70/hour to host (~$1,200/month)**
  whether it's used or not. Saving ~$0.00045 per call, it pays off above **≈ 2.7 million calls a
  month**. The app uses word list + gpt-4.1, paid per call.

### Read this before quoting a number

- **"Matches gpt-4.1", not "beats it":** the gaps between the top setups are 1 to 3 products out of
  500, from single runs.
- **The allergen answer key leans towards gpt-4.1.** Day 6 corrected 61 OFF answers where word list
  + gpt-4.1 disagreed with them; products where both were wrong together stayed wrong.
- **The photo set is small and partly a development set:** with 31 products, one product read wrong
  moves a score by ~3 points, and the reader prompt was tuned while looking at these products.
  New products may score a little lower.
- **The allergen scores assume a correct reading:** they're measured on Open Food Facts text, not
  photos. A word misread on the photo comes on top.
- **Arabic allergen precision isn't measured:** the Arabic key was never reviewed by hand, and
  every setup (the word list included) raises about twice as many alarms as it lists.
- **The student copied its teacher:** the fine-tuned model's remaining mistakes are gpt-4.1's
  (coconut read as a nut, refined soy fat as soy).

---

## Example: why this can of corn gets C, not the A printed on it

A real upload: sweet corn, water and salt. The values were read correctly.

| Bad points | | Good points | |
|---|---|---|---|
| energy 344 kJ | 1 | protein 2.5 g | 1 |
| sugars 6.7 g | 1 | fibre (not printed) | 0 |
| saturated fat 0.4 g | 0 | fruit, vegetables, legumes | **0 (assumed)** |
| salt 0.8 g | 3 | | |
| **Total** | **5** | **Total** | **1** |

Score: 5 − 1 = **4 → C**.

The producer counts the can as mostly vegetable (≥ 80%): **+5 good points**, score −1 or lower,
**A**. Labels almost never print that percentage, and the app never invents a value that isn't
printed, so it assumes 0% and says so on the page. The canned vegetables and fruit-rich products
are exactly the ones that come out worse than they deserve. Reading the printed grade, or
estimating the vegetable share from the ingredients (as Open Food Facts does), is in the v2 ideas.

---

## Cost and time

`GET /api/ai-costs` adds up the `ai_call` table (every call: step, model, tokens, time, success)
at list prices:

| Step | Model | Per product | Time |
|---|---|---|---|
| Reading | gpt-4.1-mini | ≈ $0.0015–0.0025 | 6–12 s |
| Allergens | gpt-4.1 | ≈ $0.0005 | 1.1 s |
| **Total** | | **≈ $2–3 per 1,000 products** | **≈ 7.5 s** |

Using gpt-4.1-nano for allergens would save ~$0.47 per 1,000 products, for 5x the false alarms.

---

## Run it

Requirements: Docker Desktop. For development also Java 21 and Maven.
Copy `.env.example` to `.env` and fill in your Azure OpenAI endpoint and key (never committed).

```bash
docker compose up --build        # database + app → http://localhost:8080
```

Development:

```bash
docker compose up -d postgres    # only the database
mvn -pl app spring-boot:run      # the app, from source
mvn verify                       # all tests (they start their own PostgreSQL with Testcontainers)
```

The AI evals call real models, cost money and are run by hand (examples):

```bash
mvn -pl app test -Peval "-Dtest=ReadingEvalTest"                          # photos, ~$0.24
mvn -pl app test -Peval "-Dtest=AllergenEvalTest" "-Deval.setups=combined-gpt-4.1"
```

API: `POST /api/products` (multipart `photos`, 1 to 3; 429 after the daily limit), `GET /api/products/{id}`,
`GET /api/products`, `GET /api/ai-costs`, `GET /actuator/health`.

## Deployment

Every push runs the tests and the formula check ([`ci.yml`](.github/workflows/ci.yml)); on `main`,
once they pass, the image goes to Azure Container Registry and the Container App switches to it,
then the workflow checks the site answers. GitHub logs in to Azure with **OIDC**, so no Azure
password is stored in GitHub. One-time setup: [`infra/setup-azure.ps1`](infra/setup-azure.ps1).

The AI evals are **not** in CI: they need the photos and the API key and cost money per run. They're
run before a prompt change is committed, and their reports are committed in
[`eval-food/reports/`](eval-food/reports).

## Not in v1

Nutri-Score for drinks, cheese and fats/oils (shown as "not scored"); the fruit/vegetable
percentage; barcode scanning; user accounts; health advice. Uploaded photos aren't kept after a
restart (only the results are).

## Project layout

```
app/         Spring Boot app: API, web pages, reader, Nutri-Score, allergens, cost report
eval-core/   The generic AI test code (no food code in it)
eval-food/   Food test data: photos + answer key, Open Food Facts samples, reports, baseline
training/    Python scripts: Open Food Facts samples and the fine-tuning data
infra/       One-time Azure setup
docs/        Spec, data plan, glossary, decisions log, day-by-day notes
```

## Data licence

Contains data from [Open Food Facts](https://world.openfoodfacts.org), available under the
[Open Database License](https://opendatacommons.org/licenses/odbl/1-0/).