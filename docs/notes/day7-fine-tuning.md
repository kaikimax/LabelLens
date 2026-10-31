# Day 7: fine-tuning gpt-4.1-nano for allergens

## Data (2026-10-30)

`FineTuneFileTest` asked gpt-4.1 for the allergens of the 3,000 training products (same splitter,
same instruction as the eval) and kept a product only when gpt-4.1 and Open Food Facts agreed
(decisions log, 2026-10-30).

- 2,998 answered (1 blocked by Azure's content filter, 1 rate-limited), **2,487 kept (83%)**,
  511 dropped as disagreements.
- 2,225 training + 262 validation examples; 36% with no allergen (test set: 31%).
- Rare allergens barely present: lupin 2, molluscs 2, crustaceans 5 examples.
- Cost: about $1.50 of gpt-4.1 calls. The rate limit (429) stretched the run to 2 hours with 4
  threads; the cached answers made the rerun of the 90 failed calls take 3 minutes.

## Training (2026-10-31)

Azure Foundry, Global training, base `gpt-4.1-nano-2025-04-14`, suffix `allergens`, default
hyperparameters, about 1,670 steps (3 epochs). About $1.75 at $1.50 per million training tokens.

- Training loss fell from ~1.3 to near 0 within ~50 steps; isolated spikes (hard batches) after.
- Validation loss stayed flat near 0 to the end: no sign of memorization.
- Token accuracy near 100% says little: most of each answer is fixed text, and validation only
  holds easy (agreed) products.

Deployed as `nano-ft`, Developer type (no hosting fee, deleted after 24 h).

## Results (500 test products, 1 run)

| Setup | Caught | Correct alarms | Whole set right | False alarms | s / call |
|---|---|---|---|---|---|
| word list | 96.7% | 98.6% | 94.4% | 9 | 0 |
| gpt-4.1-nano | 91.8% | 88.1% | 83.8% | 80 | |
| **gpt-4.1-nano fine-tuned** | **97.5%** | **98.0%** | **94.8%** | 13 | 0.5 |
| gpt-4.1 | 97.0% | 97.5% | 93.8% | 16 | 2.3 |
| word list + nano | 99.2% | 89.2% | 92.4% | 77 | |
| **word list + nano fine-tuned** | **99.5%** | **97.9%** | **97.0%** | 14 | 0.5 |
| word list + gpt-4.1 | 99.5% | 97.7% | 96.8% | 15 | 2.3 |

**Fine-tuning closed the gap.** Nano went from 77 false alarms (combined) to 14, gpt-4.1's level
(15), with the same 99.5% caught. Alone, it went from 83.8% to 94.8% of products fully right.
It answers in about 0.5 s instead of 2.3 s.

## Stability (3 runs of the fine-tuned nano alone, 2026-10-31)

**1000 / 1000 (case, check) pairs gave the same answer in all 3 runs (100%)**, and the three runs
scored exactly the same. At temperature 0 the fine-tuned model is fully repeatable, so the
single-run comparison above isn't luck. It doesn't make the 1-to-3-product gaps with gpt-4.1
meaningful, though: those are within what a different set of 500 products would move.

What the 3-run report adds:
- **Fish species, AI alone: 68% caught** (day 6 run 2, on the older test set: gpt-4.1 44%, base nano 16%). Likely better than its
  teacher, still missing mackerel and sardine products. The word list catches them (96%), which
  is why the combined setups don't show it.
- **Oats as gluten**: 4 misses ("flocons d'avoine"). The word list catches these too.

## Arabic test set (227 products, 1 run, 2026-10-31)

| Setup | Caught | Correct alarms | Whole set right |
|---|---|---|---|
| word list | 86.4% | 53.2% | 56.4% |
| gpt-4.1-nano | 88.3% | 54.0% | 58.1% |
| **gpt-4.1-nano fine-tuned** | **93.2%** | 53.0% | 57.7% |
| gpt-4.1 | 95.1% | 54.2% | 59.9% |
| word list + nano | rerun needed (31 calls failed) | | |
| word list + nano fine-tuned | 95.1% | 51.0% | 56.4% |
| word list + gpt-4.1 | **96.3%** | 52.0% | 58.1% |

- **Fine-tuning on French text didn't hurt Arabic; it helped.** Recall went from 88.3% to 93.2%,
  between base nano and gpt-4.1.
- **The "correct alarms" column measures the answer key, not the setups.** Every setup, including
  the word list, raises about twice as many alarms (263 to 302) as the key lists allergens (162).
  When a word list and three different models all agree on the same "false" alarms, the key is
  what's missing them: Open Food Facts detects allergens from French and English ingredient
  names, much less from Arabic, so Arabic-only products often have no allergen tags at all.
  The Arabic key was never reviewed by hand (day 6 reviewed French only). Recall is the usable
  number here; precision needs a review round first.
- The ranking matches French: gpt-4.1 and the combined setups first, then the fine-tuned nano,
  then base nano and the word list.

**Why the word list + nano run failed:** the console shows "Thread starvation or clock leap
detected (housekeeper delta=10m27s)" in the middle of the failures: the PC went to sleep for about
10 minutes, and every call in that window failed. That is probably also what broke the day 6 run.
Before long evals: turn off sleep (Windows: Settings > System > Power > Screen and sleep > Never
while plugged in).

## Read this before quoting it

- **"Matches gpt-4.1", not "beats it".** The differences between nano-ft and gpt-4.1 are 1 to 3
  products out of 500, from a single run: noise.
- **The student copied its teacher.** Its remaining mistakes are gpt-4.1's: coconut or a nut
  flavour read as a nut (0769503577708, 3451790019173), refined soy fat as soy (6111246721957,
  6111271526305, 6111246721940). Training on agreed labels can't teach what the teacher gets wrong.
- **The test key leans towards gpt-4.1.** Day 6 corrected the key where word list + gpt-4.1
  disagreed with it. Products where both were wrong together stayed wrong, so a model trained
  to imitate gpt-4.1 is favoured. The comparison with gpt-4.1 is the one most affected.
- **Rare allergens are untested**: lupin, molluscs, crustaceans have 0 to 1 test products and
  2 to 5 training examples.

## Cost: when is the fine-tuned model worth it?

Per call (about 193 input and 12 output tokens), at list prices:
- gpt-4.1: about $0.0005.
- nano (base price; fine-tuned inference may cost more, not checked): about $0.00003.

But outside the 24-hour Developer deployment, a fine-tuned model has a **hosting fee of about
$1.70 per hour (~$1,200 per month)**, whether it is used or not. Saving ~$0.00045 per call, it only
pays off above roughly **2.7 million calls per month**. For a portfolio app with a handful of users,
word list + gpt-4.1 (pay per call, no hosting fee) is the cheaper production choice; the fine-tuned
model is the better one only at scale, or where latency matters.

## Next

- Rerun word list + nano on the Arabic set (doesn't need the fine-tuned deployment).
- Review a sample of the Arabic "false alarms" before quoting any Arabic precision.
- README: the tables, the caveats above, and the cost argument.
