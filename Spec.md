# ExpenseGuard — Specification

**Status:** v1 draft · **Author:** Oussama Zaam · **Date:** 2026-10-10

## 1. Problem

Finance teams check expense reports by hand: they re-type receipt data and compare every line against a policy document nobody remembers. It's slow, it's inconsistent, and violations still slip through.

ExpenseGuard takes a receipt (image or PDF) and returns:
1. the structured data on it (merchant, date, amount, currency, category, line items), and
2. a list of policy violations, each with the exact policy clause it breaks and how confident the system is.

## 2. Goals and non-goals

**Goals (v1)**
- Extract receipt fields reliably enough to measure, and measure it (see section 8).
- Flag violations of the Atlas Meridian expense policy (`docs/expense-policy.md`), citing the clause.
- Keep every LLM call observable: tokens, cost, latency, model.
- Deployed with a public demo, CI/CD, and reproducible locally with `docker compose up`.

**Non-goals (v1)**
- Multi-tenant SaaS, billing, real authentication. A single demo user with an API key is enough.
- Full expense report workflow (submission, approval chains, reimbursement).
- Mobile app.
- Fine-tuning any model.
- Supporting any policy other than the one in `docs/`. Uploading a new policy can be v2.

## 3. Users and core flow

**User:** a finance reviewer, or an employee checking a receipt before submitting it.

1. User uploads a receipt and fills in the trip context: home city, destination city, trip dates, number of attendees (for meals), and business purpose.
2. System stores the file and runs **extraction** → a typed `ReceiptData` object.
3. System runs **policy checks** → a list of `Violation`s.
4. User sees the extracted fields (editable), the violations with clause citations, and a final status: `COMPLIANT`, `NEEDS_REVIEW`, or `VIOLATION`.
5. If the user corrects a field, the checks run again. Corrections are stored, because they're free labelled data for the eval set.

## 4. The key design decision: rules vs. LLM

Not everything should go through the LLM. Splitting the work is the core design choice of the project, and the main thing to explain in interviews.

| Check type | Example | Handled by | Why |
|---|---|---|---|
| **Numeric limits** | Meal over 400 MAD/day in Casablanca (§3.1), hotel over nightly cap (§5.1), receipt missing above 100 MAD (§2.1) | **Deterministic Java rules** | Exact, testable, free, explainable. An LLM doing arithmetic is a liability. |
| **Date/time rules** | Submitted after 90 days (§2.4), weekend expense (§6.5) | **Deterministic Java rules** | Same reasons. |
| **Duplicates** | Same merchant + date + amount (§2.5) | **SQL query** | It's a database lookup. |
| **Judgment calls** | Is this a personal expense (§1.3)? Is "Spotify Premium" a software subscription bought outside IT (§6.2)? Does the description justify a 450 MAD taxi (§4.4)? | **LLM + RAG** over the policy | Needs reading comprehension and the relevant clause text. |
| **Field extraction** | Merchant, total, currency, line items, alcohol lines | **Vision LLM** with structured output | This is where an LLM is the right tool. |

The rule engine owns anything with a number in the policy. The LLM owns extraction and fuzzy interpretation. Every violation, from either path, carries a `clauseId` (e.g. `3.5`) that links to the policy text.

## 5. Architecture

```
            ┌─────────────────────── Spring Boot app ───────────────────────┐
 Upload ──▶ │ ReceiptController                                             │
 (htmx UI   │   │                                                           │
  or REST)  │   ├─▶ StorageService ──────────────▶ Azure Blob Storage       │
            │   │                                                           │
            │   ├─▶ ExtractionService ── Spring AI ──▶ Azure OpenAI (vision)│
            │   │     └ JSON-schema validation + 1 retry on bad output      │
            │   │                                                           │
            │   └─▶ PolicyCheckService                                      │
            │         ├ RuleEngine (Java, one class per rule)               │
            │         ├ DuplicateCheck (SQL)                                │
            │         └ LlmJudge ─ PolicyRetriever ─▶ pgvector (clauses)    │
            │                    └──────────────────▶ Azure OpenAI (chat)   │
            │                                                               │
            │ LlmCallRecorder (tokens, cost, latency) ─▶ Postgres + App Insights │
            └───────────────────────────────────────────────────────────────┘
                                    │
                         Azure Database for PostgreSQL (+ pgvector)
```

**Policy ingestion** runs once at startup (or via a CLI command): split `expense-policy.md` into one chunk per numbered clause, embed each chunk, and store it with its `clauseId`. Chunking by clause instead of fixed token windows is deliberate: the policy is already structured, and it makes citations exact.

## 6. Data model

```
receipt
  id               UUID PK
  file_url         TEXT
  file_sha256      TEXT          -- exact-duplicate detection
  uploaded_at      TIMESTAMPTZ
  status           TEXT          -- PENDING | EXTRACTED | CHECKED | FAILED
  home_city        TEXT
  destination_city TEXT
  trip_start       DATE
  trip_end         DATE
  attendees        INT
  business_purpose TEXT

extraction
  id               UUID PK
  receipt_id       UUID FK
  merchant         TEXT
  expense_date     DATE
  total_amount     NUMERIC(12,2)
  currency         CHAR(3)
  total_mad        NUMERIC(12,2)
  category         TEXT          -- MEAL | LODGING | AIRFARE | TRAIN | TAXI | CAR_RENTAL | FUEL | PARKING | PHONE | SOFTWARE | BOOKS | GIFT | OTHER
  line_items       JSONB         -- [{description, amount, isAlcohol}]
  tip_amount       NUMERIC(12,2)
  model            TEXT
  corrected_by_user BOOLEAN
  created_at       TIMESTAMPTZ

violation
  id               UUID PK
  receipt_id       UUID FK
  clause_id        TEXT          -- e.g. "3.5"
  source           TEXT          -- RULE | LLM | DUPLICATE
  severity         TEXT          -- BLOCKING | REVIEW
  message          TEXT
  confidence       NUMERIC(3,2)  -- 1.00 for rules
  excess_amount_mad NUMERIC(12,2) NULL

policy_chunk
  id               UUID PK
  clause_id        TEXT
  title            TEXT
  content          TEXT
  embedding        VECTOR(1536)  -- dimension depends on embedding model

llm_call
  id               UUID PK
  receipt_id       UUID FK NULL
  purpose          TEXT          -- EXTRACTION | JUDGE | EMBEDDING
  model            TEXT
  input_tokens     INT
  output_tokens    INT
  cost_usd         NUMERIC(10,6)
  latency_ms       INT
  success          BOOLEAN
  created_at       TIMESTAMPTZ
```

Migrations are managed with Flyway.

## 7. API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/receipts` | Multipart upload: file + trip context. Returns `202` with the receipt id; processing is async. |
| `GET` | `/api/receipts/{id}` | Receipt, extraction, violations, status. |
| `PATCH` | `/api/receipts/{id}/extraction` | User corrects fields; checks re-run. |
| `GET` | `/api/receipts` | Paginated list. |
| `GET` | `/api/policy/clauses/{clauseId}` | Clause text, for citation links. |
| `GET` | `/api/metrics/llm` | Aggregated cost/latency per purpose and model. |
| `GET` | `/actuator/health` | Health check for Container Apps. |

Errors use RFC 9457 problem details (`application/problem+json`). Every request requires an `X-API-Key` header except health and the UI.

Async processing: v1 uses Spring's `@Async` with a bounded thread pool. A real queue (Azure Service Bus) is out of scope; the README will say why.

## 8. Evaluation

This section is what makes the project credible. Without it, the project is a ChatGPT wrapper.

**Extraction eval**
- Dataset: 50 receipts in `eval/receipts/`. Roughly 35 from the public SROIE dataset, 15 photographed by me (Moroccan receipts, French/Arabic text, MAD, crumpled, bad lighting).
- Labels: `eval/labels.json` with the expected `merchant`, `date`, `total`, `currency`, `category`.
- Metrics per field: exact match for date/currency/category; total within 0.01; merchant by normalized string similarity ≥ 0.85.
- Report: accuracy per field, overall, cost per receipt, p50/p95 latency.

**Policy check eval**
- 30 hand-written scenarios in `eval/policy-cases.json`. Each is a `ReceiptData` + trip context + the expected set of `clauseId`s that should fire.
- At least 1/3 must be compliant cases, to measure false positives.
- Metrics: precision and recall on violations, split by `source` (RULE vs LLM).
- Rule checks must hit 100%. Anything less is a bug, not an accuracy issue.

**CI gating**
- Rule tests run on every push (fast, free).
- The LLM eval runs on PRs that touch prompts, models or `ai/` code, and on a nightly schedule. A PR fails if any metric drops more than 3 points below `eval/baseline.json`.

## 9. Non-functional targets

| Target | Value |
|---|---|
| End-to-end processing time | p95 < 15 s per receipt |
| Cost | < $0.01 per receipt (to be confirmed on day 8) |
| Extraction accuracy (overall) | ≥ 90% on the eval set; record whatever the real number is honestly |
| Max upload size | 10 MB; JPEG, PNG, PDF only |
| Malformed LLM output | Validate → 1 retry with the error fed back → mark `FAILED` and surface it |
| Secrets | None in the repo. Azure via OIDC in CI, managed identity at runtime where possible. |

## 10. Tech stack

Java 21 · Spring Boot 3 · Spring AI (Azure OpenAI starter) · PostgreSQL + pgvector · Flyway · Thymeleaf + htmx · JUnit 5 + Testcontainers · Docker · GitHub Actions · Azure Container Apps, Azure Database for PostgreSQL Flexible Server, Blob Storage, Application Insights.

## 11. Risks

| Risk | Mitigation |
|---|---|
| Azure OpenAI quota or model not available in region | Check on day 2 before writing extraction code. Spring AI lets me swap provider by config. |
| Overlap with my employer's T&E product | Built on personal time and hardware, no company code or data, fictional policy. Contract checked. |
| Running out of the $100 credit | Budget alert at $25; stop Postgres when idle; scale-to-zero app. |
| Scope creep (auth, multi-policy, queue) | Non-goals listed in section 2. Anything new goes in `docs/v2-ideas.md`. |
| SROIE receipts are Malaysian, not Moroccan | That's why 15 receipts are my own. Report accuracy for both subsets separately. |

## 12. Definition of done

- [ ] Live demo URL works and the app scales back to zero.
- [ ] `docker compose up` runs the full app locally.
- [ ] CI: build, unit tests, Testcontainers integration tests, image push, deploy.
- [ ] Eval results committed and shown in the README with the real numbers.
- [ ] README: problem, architecture diagram, rules-vs-LLM decision, eval table, cost per 1,000 receipts, what I'd do next.
- [ ] 2-minute demo video linked from the README.