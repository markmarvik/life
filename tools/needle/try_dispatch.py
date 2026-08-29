#!/usr/bin/env python3
"""Desktop eval of PatternNeedle. Not shipped to phones.

Mirrors app/.../needle/PatternNeedle.kt so we can dump phrases without
Android. Optional: `pip install cactus-needle` and pass --cactus to try
the tiny tool-calling model on a laptop. Phones never import this file.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path

TOOLS_PATH = Path(__file__).with_name("life_tools.json")

SPEND = [
    "spent", "spend", "paid", "pay", "bought", "buy", "cost", "expense",
    "potrosio", "potrosila", "platio", "platila", "kupio", "kupila",
]
EARN = [
    "earned", "earnt", "income", "salary", "received", "got paid",
    "zaradio", "zaradila", "plata", "primio", "primila", "dobio", "dobila",
]
HABIT_VERB = [
    "logged", "log", "did", "drank", "took", "done",
    "popio", "popila", "uzeo", "uzela", "uradio", "uradila",
    "odradio", "odradila",
]
NOTE_VERB = ["note", "remember", "idea", "thought", "beleska", "zapamti"]
WANT_VERB = ["want", "commit", "zelim", "hocu"]
BALANCE = ["balance", "how much", "stanje", "koliko imam", "koliko ima"]
COMPLETE = ["done with", "finished", "complete want", "mark done", "zavrsio", "zavrsila"]
MISS = ["didn't", "didnt", "skipped", "missed", "nisam", "propustio", "propustila"]
CURRENCY = ["dinara", "dinar", "rsd", "eur", "euro", "euros", "usd", "dollars", "dollar"]
STOP = set(
    SPEND + EARN + HABIT_VERB + NOTE_VERB + WANT_VERB + CURRENCY
    + ["a", "an", "the", "on", "for", "at", "to", "of", "and", "i", "my"]
)
CATEGORY_HINTS = {
    "food": "Food", "hrana": "Food", "eat": "Food", "lunch": "Food",
    "grocer": "Groceries", "shop": "Shopping", "rent": "Rent", "kirija": "Rent",
    "transport": "Transport", "bus": "Transport", "fuel": "Transport",
    "health": "Health", "fun": "Fun", "bill": "Bills", "racun": "Bills",
    "salary": "Salary", "plata": "Salary",
}
CATALOG = [
    "Morning sunlight", "Meditate", "Brush + floss", "Make the bed",
    "Morning hydrate", "Cold shower / shower", "Protein first", "Lunch walk",
    "Stand / stretch break", "Move your body", "Strength training",
    "Read before bed", "Gratitude / 3 things", "No screens wind-down",
]


def fold(s: str) -> str:
    table = str.maketrans("ščćžđ", "scczd")
    return s.lower().translate(table)


def fuzzy(a: str, b: str) -> float:
    aa, bb = fold(a), fold(b)
    if not aa or not bb:
        return 0.0
    if aa == bb:
        return 1.0
    if aa in bb or bb in aa:
        return 0.85
    # token overlap + prefix
    at, bt = aa.split(), bb.split()
    hits = sum(1 for t in at if any(u.startswith(t) or t.startswith(u) for u in bt))
    return min(0.95, 0.4 + 0.2 * hits)


def strip_verbs(raw: str, verbs: list[str]) -> str:
    s = raw
    for v in sorted(verbs, key=len, reverse=True):
        s = re.sub(rf"(?i)\b{re.escape(v)}\b", " ", s)
    return re.sub(r"\s+", " ", s).strip(" ,.:!")


def leftover(raw: str) -> str:
    drop = STOP | set(CURRENCY)
    parts = []
    for tok in fold(raw).split():
        if not tok or re.fullmatch(r"\d+(?:[.,]\d+)?", tok):
            continue
        if any(tok == d or (len(d) > 3 and d in tok) for d in drop):
            continue
        parts.append(tok)
    return " ".join(parts)


def first_amount(lower: str) -> float | None:
    m = re.search(r"(?<![a-z])(\d+(?:[.,]\d+)?)(?![a-z])", lower)
    if not m:
        return None
    return float(m.group(1).replace(",", "."))


def parse_date(lower: str, today: date) -> str | None:
    if "tomorrow" in lower or "sutra" in lower:
        return (today + timedelta(days=1)).isoformat()
    if "today" in lower or "danas" in lower:
        return today.isoformat()
    m = re.search(r"\d{4}-\d{2}-\d{2}", lower)
    return m.group(0) if m else None


def to_minor(amount: float, currency: str) -> int:
    # ISO-ish: most currencies 2 fraction digits, including RSD.
    digits = 0 if currency.upper() in {"JPY", "KRW"} else 2
    return int(round(amount * (10 ** digits)))


@dataclass
class Decision:
    kind: str
    confidence: float
    engine: str = "pattern"
    payload: dict | None = None

    def as_dict(self) -> dict:
        d = {"kind": self.kind, "confidence": self.confidence, "engine": self.engine}
        if self.payload:
            d.update(self.payload)
        return d


def dispatch(text: str, *, today: date, currency: str = "RSD", open_wants: list[str] | None = None) -> Decision:
    raw = text.strip()
    if not raw:
        return Decision("unknown", 0.0)
    lower = fold(raw)
    wants = open_wants or []

    if any(w in lower for w in BALANCE):
        return Decision("balance", 0.9)

    if any(w in lower for w in MISS):
        rest = strip_verbs(raw, MISS)
        if rest:
            return Decision("miss_want", 0.7, payload={"text": rest})

    if any(w in lower for w in COMPLETE) or lower.startswith("done "):
        rest = strip_verbs(raw, COMPLETE + ["done"])
        if wants:
            hit = rest if not rest else max(wants, key=lambda w: fuzzy(rest, w))
            return Decision("complete_want", 0.75, payload={"text": hit})

    amount = first_amount(lower)
    if amount is not None:
        spent = any(w in lower for w in SPEND)
        earned = any(w in lower for w in EARN)
        if spent or earned or amount >= 20:
            cat = next((v for k, v in CATEGORY_HINTS.items() if k in lower), "")
            payee = " ".join(
                tok for tok in leftover(raw).split()
                if not any(k in tok for k in CATEGORY_HINTS)
            )
            return Decision(
                "finance",
                0.88 if spent or earned else 0.62,
                payload={
                    "sign": "in" if earned and not spent else "out",
                    "amount_minor": to_minor(amount, currency),
                    "payee": payee,
                    "category": cat,
                    "memo": raw,
                },
            )

    is_want = any(w in lower for w in WANT_VERB)
    is_note = any(w in lower for w in NOTE_VERB)
    if is_want or is_note:
        d = parse_date(lower, today)
        body = strip_verbs(raw, WANT_VERB + NOTE_VERB + ["today", "tomorrow", "danas", "sutra"]) or raw
        return Decision(
            "note",
            0.8,
            payload={"body": body, "want_date": (d or today.isoformat()) if is_want else d},
        )

    stripped = strip_verbs(raw, HABIT_VERB)
    name = stripped or raw
    verb = any(lower == v or lower.startswith(v + " ") for v in HABIT_VERB)
    known = max(CATALOG, key=lambda n: fuzzy(name, n))
    score = fuzzy(name, known)
    if score >= 0.62 and (verb or score >= 0.72):
        return Decision("habit", 0.9 if verb else 0.78, payload={"name": known})
    if verb and name:
        return Decision("habit", 0.82, payload={"name": name})

    if len(raw) <= 40:
        return Decision("habit", 0.5, payload={"name": raw})
    return Decision("note", 0.5, payload={"body": raw, "want_date": None})


def self_test() -> int:
    today = date(2026, 8, 29)
    cases = []

    d = dispatch("popio kreatin", today=today)
    cases.append(d.kind == "habit" and d.payload["name"].lower() == "kreatin" and d.confidence >= 0.55)

    d = dispatch("logged creatine", today=today)
    cases.append(d.kind == "habit" and d.payload["name"].lower() == "creatine")

    d = dispatch("spent 450 dinara lidl food", today=today, currency="RSD")
    cases.append(
        d.kind == "finance"
        and d.payload["sign"] == "out"
        and d.payload["amount_minor"] == 45000
        and "lidl" in d.payload["payee"]
        and d.payload["category"] == "Food"
    )

    d = dispatch("want call dentist tomorrow", today=today)
    cases.append(
        d.kind == "note"
        and d.payload["want_date"] == "2026-08-30"
        and "dentist" in d.payload["body"].lower()
    )

    d = dispatch("koliko imam", today=today)
    cases.append(d.kind == "balance")

    d = dispatch("done call dentist", today=today, open_wants=["Call dentist"])
    cases.append(d.kind == "complete_want")

    d = dispatch("Meditate", today=today)
    cases.append(d.kind == "habit" and d.payload["name"] == "Meditate")

    failed = [i for i, ok in enumerate(cases) if not ok]
    if failed:
        print("FAIL", failed, file=sys.stderr)
        return 1
    print("ok", len(cases), "checks")
    return 0


def try_cactus(text: str) -> dict | None:
    try:
        from cactus_needle import Needle  # type: ignore
    except Exception:
        return None
    schema = json.loads(TOOLS_PATH.read_text())
    n = Needle(tools=schema["tools"])
    return {"engine": "cactus", "raw": str(n(text))}


def main() -> int:
    p = argparse.ArgumentParser(description="Eval PatternNeedle on the desktop.")
    p.add_argument("text", nargs="*", help="Phrase to dispatch")
    p.add_argument("--test", action="store_true")
    p.add_argument("--cactus", action="store_true", help="Also try cactus-needle if installed")
    p.add_argument("--today", default="2026-08-29")
    p.add_argument("--currency", default="RSD")
    args = p.parse_args()
    if args.test:
        return self_test()
    if not args.text:
        p.print_help()
        print()
        return self_test()
    today = date.fromisoformat(args.today)
    phrase = " ".join(args.text)
    out = dispatch(phrase, today=today, currency=args.currency).as_dict()
    if args.cactus:
        c = try_cactus(phrase)
        out["cactus"] = c or "not installed (pip install cactus-needle)"
    json.dump(out, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
