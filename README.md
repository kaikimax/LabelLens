# LabelLens

Photograph a food label → get its nutrition values, Nutri-Score and allergens, with every AI step measured by automatic tests.

Full design: [docs/SPEC.md](docs/SPEC.md) · Data: [docs/DATA_PLAN.md](docs/DATA_PLAN.md) · Terms: [docs/GLOSSARY.md](docs/GLOSSARY.md)

> Work in progress.

## Run it locally

Requirements: Java 21, Maven, Docker Desktop (running).

Copy `.env.example` to `.env` and fill in your Azure OpenAI endpoint and key (`.env` is never committed).

```bash
docker compose up -d            # start PostgreSQL
mvn -pl app spring-boot:run     # start the app on http://localhost:8080
```

Try it:

```bash
curl -F "photos=@back.jpg" -F "photos=@front.jpg" http://localhost:8080/api/products
curl http://localhost:8080/api/products
curl http://localhost:8080/actuator/health
```

## Run the tests

```bash
mvn verify
```

Tests start their own PostgreSQL in Docker (Testcontainers), so they don't touch your local database.

## Project layout

```
app/         Spring Boot API
eval-food/   Test data for food labels (photos + correct answers)
training/    Python scripts for preparing fine-tuning data
docs/        Spec, data plan, glossary
```

## Data licence

Contains data from [Open Food Facts](https://world.openfoodfacts.org), available under the [Open Database License](https://opendatacommons.org/licenses/odbl/1-0/).
