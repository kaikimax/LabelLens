# LabelLens — Specification

**Version:** v1 · **Author:** Oussama Zaam · **Date:** 2026-10-11

Words in **bold** the first time they appear are explained in `GLOSSARY.md`.

---

## 1. What LabelLens does

You take photos of the back of a food package: the nutrition table and the ingredients list. LabelLens returns:

1. **The nutrition values** it read from the photo (sugar, salt, fat, etc., per 100 g).
2. **The Nutri-Score** (the A to E grade printed on many European products), calculated from those values.
3. **The allergens** found in the ingredients (gluten, milk, nuts, etc.).

That's the product. The real point of the project is section 8: **automatic tests that measure how often the AI gets things right**, and that stop me from shipping a version that's worse than the last one.

## 2. What's in version 1, and what isn't

**In v1**
- Reading nutrition values and ingredients from photos, using an AI model that can read images.
- Calculating the Nutri-Score with normal Java code.
- Finding allergens with an AI model, and comparing two ways of doing it (see section 9).
- Tests that score the AI, run automatically on every code change.
- Tracking what each AI call costs and how long it takes.
- A live website anyone can try, plus the option to run it locally with one command.

**Not in v1** (written down so I don't drift into them)
- Barcode scanning.
- Explaining additives (E-numbers) with sources. Good idea, but it would add a second AI technique. Moved to v2.
- Nutri-Score for drinks, cheese and oils, which use special rules. v1 shows "not scored" for these.
- User accounts.
- Any health or diet advice. The app only reports what's on the label.

## 3. How it works, step by step

1. The user uploads 2 photos: the nutrition table and the ingredients list.
2. **Reading (AI):** an AI model looks at the photos and returns the values as JSON, a fixed format the Java code can load into an object. If the JSON is broken or missing fields, the app asks the model once more, telling it what was wrong. If it fails again, the product is marked as failed.
3. **Sanity checks (Java):** simple checks that catch reading mistakes. For example, sugar can't be higher than total carbohydrates, and nothing can be more than 100 g per 100 g. Problems are shown as warnings.
4. **Scoring (Java):** the Nutri-Score formula turns the values into points, and the points into a grade. The page shows every point, so the user can see why the product got a D.
5. **Allergens (AI):** a model reads the ingredients text and returns the allergens it found.
6. The page shows all the results.

## 4. Which parts use AI, and which don't

The main design rule: **if there's a formula or a fixed rule, write normal code. Use AI only for what code can't do.**

| Step | AI or code? | Why |
|---|---|---|
| Reading the photo | AI | Code can't reliably read blurry, curved, French/Arabic packaging. |
| Sanity checks | Code | Simple comparisons. Always correct, easy to test. |
| Nutri-Score | Code | It's a published formula. An AI could make arithmetic mistakes and give different answers each time. |
| Finding allergens | AI | Allergens hide behind many names ("lactosérum" is milk, "épeautre" is gluten). A keyword list would miss many. |

This rule is the most important thing to explain in an interview. It shows you use AI where it helps, not everywhere.

## 5. How it's built

```
 Browser ──▶ Spring Boot app
               ├─ saves photos ───────────────▶ Azure Blob Storage
               ├─ reads the label ── AI call ─▶ Azure OpenAI (model that reads images)
               ├─ sanity checks + Nutri-Score   (plain Java, no AI)
               ├─ finds allergens ── AI call ─▶ Azure OpenAI (small model)
               └─ records cost & time of every AI call
                                     │
                                     ▼
                     PostgreSQL database (Azure)
```

**Code folders:**

```
labellens/
  app/         The Spring Boot website and API
  eval-core/   The AI testing code (knows nothing about food, see 8.1)
  eval-food/   The test data for food labels + saved scores
  training/    Small Python scripts to prepare data for fine-tuning (section 9)
  docs/        This spec, the data plan, the glossary
```

## 6. Database tables

```
product           one row per upload: id, date, status, photo links
label_reading     what the AI read: name, sugar, salt, sat. fat, energy,
                  fibre, protein, ingredients text, language, warnings
nutri_score       the grade (or "not scored"), and every point that led to it
allergen          one row per allergen found, and which model found it
ai_call           every AI call: what for, which model, tokens used,
                  cost in $, time in ms, success or failure
```

Tables are created and updated with Flyway, so the database structure is versioned with the code.

## 7. API

| Method | Path | What it does |
|---|---|---|
| `POST` | `/api/products` | Upload the photos and get all results back. |
| `GET` | `/api/products/{id}` | See one product's results. |
| `GET` | `/api/products` | List products, page by page. |
| `GET` | `/api/ai-costs` | Total cost and average time of AI calls, by step and model. |
| `GET` | `/api/eval/latest` | The latest test scores, shown on a page in the app. |
| `GET` | `/actuator/health` | Lets Azure check the app is running. |

## 8. Testing the AI (the core of the project)

### 8.0 The idea

Normal tests check that code gives the right answer. The AI is not normal code: the same photo can be read slightly differently, and a model change can make it worse without anything "breaking."

So I collect examples where **I already know the correct answer**, run the AI on them, and count how often it's right. This is called an **eval** (short for evaluation).

### 8.1 The testing code is not tied to food

The testing code in `eval-core/` only does four generic things:

1. **Load test cases:** an input (for example, a photo) plus the correct answer.
2. **Run** the AI step on each input.
3. **Compare** the AI's answer with the correct one, and count the results.
4. **Save a report**, and compare it with the previous best scores.

It doesn't know what a nutrition table is. That makes it reusable: to test an AI that reads receipts or invoices instead, you'd write new test cases, not new testing code.

### 8.2 What gets tested

**Test 1: Reading the photo**
- Test cases: 31 products I photograph myself, where I typed the correct values from the package by hand.
- Score: for each value (sugar, salt…), the percentage of products where the AI read it correctly, allowing for rounding.
- Split by situation: French vs Arabic labels, clear vs blurry photos. A finding like "accuracy drops from 94% to 71% on curved bottles" is exactly what makes the project interesting.

**Test 2a: The Nutri-Score formula**
- Test cases: about 5,000 products from **Open Food Facts** that already have an official grade.
- Score: how often my Java code gives the same grade. It should be close to 100%. Every difference is either a bug in my code or a special case I need to understand.
- This is a normal unit test, just a big one, and it's free because no AI is involved.

**Test 2b: The full chain, photo to grade**
- Test cases: the same 31 photographed products.
- Score: how often the final grade is right, and **how often a small reading mistake changes the grade**. For example, misreading salt as 0.3 g instead of 0.8 g can turn a C into a B. This shows how errors travel through the system.

**Test 3: Finding allergens**
- Test cases: 500 products from Open Food Facts (not used for training, see section 9) plus my 31.
- Two numbers per allergen:
  - **Missed allergens:** the product contains milk, and the AI didn't say so. This is the dangerous mistake.
  - **False alarms:** the AI says milk, and there's none. Annoying, but not dangerous.
- Since a missed allergen is worse, **the main score is "how many real allergens did the AI catch"** (in AI terms, **recall**). Choosing the score based on which mistake is worse is a good interview point.

### 8.3 What a report looks like

```
Test 1 – Reading            sugar 95%   salt 88%   energy 97%   (Arabic labels: salt 79%)
Test 2a – Formula           4,987 / 5,000 grades match (99.7%)
Test 2b – Photo to grade    34 / 40 correct; 4 grade changes caused by salt misreads
Test 3 – Allergens          caught 96% of real allergens; 7% false alarms
Cost                        $0.004 per product, 6.2 s on average
```

(Numbers here are invented examples. The real ones get filled in after running the tests.)

## 9. The fine-tuning experiment

**Fine-tuning** means taking an existing AI model and training it a bit more on your own examples, so it gets better at one specific task. You don't need to understand the math: Azure does the training. My job is to prepare good examples and measure the result fairly.

**The question I'm answering:** for finding allergens, is it worth fine-tuning a small, cheap model, or is a big model with good instructions enough?

**How:**
1. Take about 3,000 products from Open Food Facts, each with its ingredients and its allergens.
2. Write them in the format Azure expects: one line per product saying "for this ingredients text, the answer is these allergens."
3. Upload the file to Azure and start the training. Azure produces a new model.
4. Run Test 3 on three setups and compare:

| Setup | Allergens caught | False alarms | Cost per 1,000 products | Time per product |
|---|---|---|---|---|
| Big model, instructions only | | | | |
| Small model, instructions only | | | | |
| Small model, fine-tuned | | | | |

**The most important rule:** the 500 test products must **never** be used for training. Otherwise it's like giving a student the exam answers before the exam: the score looks great and means nothing. Products are split using their barcode, so a product is either in the training group or the test group, never both. My 40 photographed products are never used for training.

**Whatever the result, it goes in the README.** If fine-tuning doesn't help, "I measured it, and here's why it wasn't worth it" is still a strong result.

**Cost control:** Azure normally charges per hour for hosting a fine-tuned model. I use the **Developer tier**, which has no hourly charge. The deployment deletes itself after 24 hours, but the trained model is kept, so I redeploy it whenever I run the tests.

## 10. Automatic checks on every code change (CI)

Using GitHub Actions, every time I push code:

| When | What runs | Cost |
|---|---|---|
| Every push | Normal Java tests + Test 2a (formula) | Free |
| When I change AI-related code or instructions | A small version of Tests 1 and 3 (10 photos, 100 products) | A few cents |
| Once a night, or when I ask | All tests, full size | About $1–2 |

The last good scores are saved in `eval-food/baseline.json`. If a change makes any main score drop by more than 3 points, the check fails and the change can't be merged until I fix it or explain it.

## 11. Rules that apply everywhere

- Every AI call is recorded with its cost and duration.
- Uploads: max 3 images, 10 MB each, JPEG or PNG.
- No passwords or keys in the code. GitHub connects to Azure without stored secrets (using **OIDC**).
- A visible note in the app: label information only, not diet or medical advice.
- Open Food Facts data is free to use with credit given. The credit goes in the README and the app footer.

## 12. Tools

Java 21 · Spring Boot 3 · Spring AI (to call Azure OpenAI from Java) · PostgreSQL · Flyway · Thymeleaf + htmx (simple web pages without a JavaScript framework) · JUnit 5 + Testcontainers (tests with a real database in Docker) · Docker · GitHub Actions · Azure: Container Apps, PostgreSQL, Blob Storage, Application Insights, OpenAI · Python (only to prepare training data).

## 13. Two-week plan

| Day | Work |
|---|---|
| 1 | Spec, data plan, photograph 20 products. |
| 2 | Spring Boot project, database, photo upload. Check which Azure AI models I can use. |
| 3 | AI reading of the photo + sanity checks. |
| 4 | Nutri-Score in Java + Test 2a. |
| 5 | `eval-core` testing code + Tests 1 and 2b. Photograph and type in the other 20 products. |
| 6 | Allergens with a big model + Test 3. This is the score to beat. |
| 7–8 | Prepare training data in Python, fine-tune, run the three-way comparison. |
| 9 | Cost and time tracking, error handling. |
| 10–11 | Web pages, Docker, GitHub Actions, deploy to Azure. |
| 12 | README with results, diagram and decisions; 2-minute demo video. |
| 13–14 | Spare time. Something will take longer than planned. |

## 14. Risks

| Risk | What I'll do |
|---|---|
| The AI model I need isn't available in my Azure region | Check on day 2. Spring AI lets me switch provider with configuration only. |
| Spending the $100 credit | Alert at $25. Stop the database when not working. App costs nothing when nobody uses it. Small tests in CI, full tests only at night. |
| Open Food Facts data has mistakes | That's why my 40 hand-checked products are scored separately. I trust that number most. |
| My Nutri-Score uses a different version of the formula than Open Food Facts | Check which version their data uses before Test 2a. |
| Adding features instead of finishing | Section 2 lists what's out. New ideas go in `docs/v2-ideas.md`. |

## 15. Finished when

- [ ] The live website works.
- [ ] `docker compose up` runs everything on any computer.
- [ ] GitHub Actions runs the tests, blocks a worse version, and deploys.
- [ ] All result tables in sections 8 and 9 contain real numbers.
- [ ] README explains: the problem, the design, the "AI vs code" rule, the test results, the fine-tuning verdict, the cost per 1,000 products, and how the testing code could test other AI systems.
- [ ] 2-minute demo video.
