"""Print the test-set text and answer key for some products, for the hand review of an allergen eval.

    .venv\\Scripts\\python training\\show_allergen_cases.py 0769503575353 6111255062010 > review.txt
    .venv\\Scripts\\python training\\show_allergen_cases.py --report eval-food\\reports\\allergens-fr-wordlist-....txt > review.txt

With --report, every product listed in that report's DISAGREEMENTS section is printed.
Write to a file and open it in VS Code: the console code page garbles accents and Arabic.
"""
import json
import re
import sys
from pathlib import Path

TEST_SET = Path("eval-food/data/off/allergen_test.jsonl")


def codes_from_report(report: Path) -> list[str]:
    codes, inside = [], False
    for line in report.read_text(encoding="utf-8").splitlines():
        if line.startswith("DISAGREEMENTS"):
            inside = True
        elif inside and line.startswith("RUNS WITH NO USABLE ANSWER"):
            break
        elif inside and re.fullmatch(r"\d+", line.strip()) and not line.startswith(" "):
            codes.append(line.strip())
    return codes


def main() -> None:
    # Windows redirects stdout in the console code page (MS932 here): accents and Arabic would crash.
    sys.stdout.reconfigure(encoding="utf-8")
    args = sys.argv[1:]
    if not args:
        sys.exit(__doc__)
    if args[0] == "--report":
        wanted = codes_from_report(Path(args[1]))
    else:
        wanted = args
    products = {}
    test_set = Path(args[2]) if args[0] == "--report" and len(args) > 2 else TEST_SET
    with test_set.open(encoding="utf-8") as f:
        for line in f:
            row = json.loads(line)
            products[row["code"]] = row
    for code in wanted:
        p = products.get(code)
        if p is None:
            print(f"{code}: not in {test_set}\n")
            continue
        details = [p.get("lang"), p.get("origin"),
                   "had markup" if p.get("had_markup") or p.get("had_html") else None]
        print(f"=== {code}  ({', '.join(d for d in details if d)})")
        print(f"key allergens: {p.get('allergens')}")
        print(f"key traces:    {p.get('traces')}"
              + (f"   (OFF: {p['traces_off']})" if p.get("traces_off") not in (None, p.get("traces")) else ""))
        if p.get("key_fixed"):
            print(f"key fixed:     {p['key_fixed']}")
        print(f"text: {p['text']}")
        print(f"verdict: ")
        print()


if __name__ == "__main__":
    main()
