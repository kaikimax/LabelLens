import pyarrow.parquet as pq
import pandas as pd

# 1. All column names and types, without loading the data
schema = pq.read_schema("food.parquet")
for field in schema:
    print(field.name, "->", field.type)

# 2. Some counts
df = pd.read_parquet("food.parquet", columns=["nutriscore_grade", "countries_tags"])
print("\nNutri-Score grades:")
print(df["nutriscore_grade"].value_counts())

morocco = df["countries_tags"].apply(lambda tags: tags is not None and "en:morocco" in tags)
print("\nMoroccan products:", morocco.sum())