# LabelLens — Glossary

Every term an interviewer could ask about, explained with an example from this project. If you can explain each one in your own words, you can defend the project.

---

## AI basics

**Model**
A program trained on huge amounts of text (and sometimes images) that can answer questions. You send it a message, it sends back an answer. In this project: GPT models hosted on Azure OpenAI.

**Prompt / instructions**
The message you send to the model. Example: "Read this nutrition table and return the values as JSON." Changing the prompt changes the answers, which is why prompts need testing like code does.

**Token**
Models read and write text in small pieces called tokens, roughly ¾ of a word each. You pay per token, both for what you send and what you get back. Images are converted to tokens too.

**Vision model**
A model that can look at images as well as read text. Used here to read the photos.

**Structured output / JSON output**
Asking the model to answer in a fixed JSON format instead of free text, so Java can load it into an object. Models sometimes break the format, which is why the app checks it and retries once.

**Hallucination**
When a model states something false with confidence. Example: inventing a salt value that's not on the label because the photo is blurry. Your tests measure how often this happens.

---

## Testing the AI

**Eval (evaluation)**
Testing an AI by running it on examples where you already know the right answer, and counting how often it's right. Like a unit test, but the result is a percentage instead of pass/fail.

**Test set**
The examples used in an eval. Here: your 40 photographed products, and 500 Open Food Facts products.

**Ground truth**
The correct answer for each test example. Here: the values you typed from the package.

**Baseline**
The score you're trying to beat, or trying not to fall below. Two meanings in this project:
- In CI: the last good scores, saved in `baseline.json`.
- In the fine-tuning experiment: the big model's score, which the fine-tuned model has to beat.

**Regression**
When something that used to work gets worse. Example: you change the prompt, and salt is now read correctly 80% of the time instead of 90%.

**Tolerance**
How far a value can be from the correct one and still count as correct. Labels round numbers, so "exactly equal" would count rounding as a mistake. Choosing the tolerance is part of designing the eval (LabelLens: day 5, step 2).

**Stability (run-to-run variance)**
Ask the same model the same question twice and you can get two different answers. Stability is how often repeated runs agree. Low stability means a single run proves nothing, which is why the LabelLens eval runs each product several times.

**Accuracy**
Correct answers divided by total answers. Used for reading values: "sugar read correctly in 38 of 40 products = 95%."

**Recall** (= "how many did we catch")
Of all the allergens really in the products, the percentage the AI found. If 100 products contain milk and the AI said "milk" for 96 of them, recall is 96%. Low recall means **missed allergens**, the dangerous mistake.

**Precision** (= "how many alarms were real")
Of all the times the AI said "milk," the percentage where milk really was there. Low precision means **false alarms**.

**Why both matter:** an AI that says "contains everything" for every product catches 100% of allergens (perfect recall) but is useless (terrible precision). You need both numbers, and you choose which one matters most. Here it's recall, because missing an allergen can hurt someone.

**F1 score**
One number combining precision and recall. Useful for ranking, but you'll mostly talk about recall and false alarms separately, because they're easier to explain.

**Error propagation**
When a mistake in an early step causes a wrong result later. Example: salt misread → wrong points → wrong Nutri-Score grade. Test 2b measures this.

---

## Training

**Training data**
Examples a model learns from. Here: 3,000 Open Food Facts products, each with its ingredients and its correct allergens.

**Fine-tuning**
Training an existing model a bit more on your own examples, so it improves at one specific task. You don't change how the model works; you show it many examples of the task. Azure does the actual training: you upload a file and wait.

**Train / test split**
Dividing your data into two separate groups: one to learn from (practice questions) and one to measure with (the exam). They must never overlap.

**Data leakage**
When test examples end up in the training data, even indirectly (the same product under two barcodes, say). The model has "seen the exam," so its score is falsely high. The barcode split and duplicate removal prevent this.

**Overfitting**
When a model memorizes its training examples instead of learning the general task. It scores well on training data and badly on new data. That's another reason to always test on data the model has never seen.

**Developer tier** (Azure)
A cheap way to deploy a fine-tuned model for testing: no hourly fee, but it's deleted after 24 hours. The trained model is kept, so you can redeploy it.

---

## Building and shipping

**CI (Continuous Integration)**
A server that automatically builds and tests your code every time you push. Here: GitHub Actions.

**CD (Continuous Deployment)**
Automatically putting the new version online when all checks pass.

**Eval gate**
A CI step that runs the AI tests and fails the build if a score drops below the baseline. It stops a worse AI version from going online.

**Sanity check**
A simple rule that catches obviously wrong data. Example: sugar can't be more than total carbohydrates.

**Deterministic**
Gives the same output every time for the same input. Normal Java code is deterministic; AI models usually aren't. That's why the Nutri-Score is calculated in Java.

**Latency**
How long something takes. "p95 latency of 8 seconds" means 95% of requests finish within 8 seconds. It's used instead of the average, because the average hides the slow requests.

**OIDC** (in GitHub Actions)
A way for GitHub to log in to Azure without storing a password. Azure trusts GitHub to say which repository is asking.

**Spring AI**
A Spring library for calling AI models from Java, the same way Spring Data calls databases. Switching from Azure OpenAI to another provider is mostly a configuration change.

**Testcontainers**
A Java library that starts a real PostgreSQL database in Docker during tests, then deletes it. Tests run against a real database, not a fake one.

---

## Food terms

**Nutri-Score**
A grade from A (best) to E (worst) used on food in France, Belgium, Spain and elsewhere. It's calculated with a published formula: points for "bad" nutrients (energy, sugar, saturated fat, salt), minus points for "good" ones (fibre, protein, fruit and vegetables). The total maps to a letter.

**Allergen**
An ingredient that can cause allergic reactions. EU law lists 14 that must be highlighted: gluten, milk, eggs, nuts, peanuts, soy, fish, shellfish, molluscs, celery, mustard, sesame, sulphites and lupin.

**Open Food Facts**
A free, volunteer-built database of over 3 million food products. It gives you thousands of real examples with correct answers, without labelling them yourself.
