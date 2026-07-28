#!/usr/bin/env python3
"""Regenerates catalog/deals.json from tools/services.json.

The catalog is generated data, not hand-typed: names, descriptions and icons
come from each app's Play listing, and trial lengths come from the service's own
pricing page. This is the same extraction the app performs on device
(TrialTextExtractor / TrialProbeSource) — keep the two in sync when either
changes.

Adding a service means adding four fields to tools/services.json and re-running
this. Entries the probe cannot confirm are not invented: they go to the
watchlist, and the app promotes them itself once the service advertises an offer.

    python3 tools/build_catalog.py [--jobs 8] [--out catalog/deals.json]
"""

from __future__ import annotations

import argparse
import collections
import datetime
import html
import json
import re
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent.parent
SERVICES = ROOT / "tools" / "services.json"
CATALOG = ROOT / "catalog" / "deals.json"
BUNDLED = ROOT / "app" / "src" / "main" / "assets" / "deals_catalog.json"

UA = (
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/120.0.0.0 Mobile Safari/537.36"
)

# Mirrors TrialTextExtractor.patterns.
TRIAL_PATTERNS = [
    re.compile(p, re.I)
    for p in [
        r"(\d{1,3})\s*[-‑–]?\s*(days?|months?|weeks?)\s*(?:of\s+)?(?:free\s*trial|free\b|trial\b)",
        r"free\s*(?:trial\s*)?(?:for\s+)?(\d{1,3})[\s-]*(days?|months?|weeks?)",
        r"try\s+(?:\w+\s+){0,3}free\s+for\s+(\d{1,3})[\s-]*(days?|months?|weeks?)",
        r"(\d{1,3})\s*(дн\w+|месяц\w*|недел\w+)\s*(?:бесплатно|пробн\w*)",
        r"бесплатн\w*\s*(?:период\s*)?(?:на\s*)?(\d{1,3})\s*(дн\w+|месяц\w*|недел\w+)",
        r"пробн\w*\s*(?:период|подписк\w*)?\s*(?:на\s+)?(\d{1,3})\s*(дн\w+|месяц\w*|недел\w+)",
    ]
]

MAX_DAYS = 190
# Past this, a bare "free" period needs the word "trial": "3 months of free" and
# "6 months FREE" are bonuses on an annual plan, not trials.
LONG_OFFER_DAYS = 62

TITLE_PREFIX = re.compile(r"^(Приложения в Google Play|Игры в Google Play)\s*[–-]\s*")


def fetch(url: str, lang: str = "en-US,en;q=0.9,ru;q=0.8", timeout: int = 25) -> str:
    result = subprocess.run(
        ["curl", "-sSL", "-m", str(timeout), "-A", UA, "-H", f"Accept-Language: {lang}", url],
        capture_output=True,
        text=True,
        errors="ignore",
    )
    return result.stdout


def to_days(amount: str, unit: str) -> int | None:
    n, u = int(amount), unit.lower()
    if u.startswith(("month", "месяц")):
        return n * 30
    if u.startswith(("week", "недел")):
        return n * 7
    if u.startswith(("day", "дн")):
        return n
    return None


def flatten(page: str) -> str:
    """Tags go, <script> stays — SPA copy lives in the JSON payload."""
    text = (
        page.replace("\\u002F", "/")
        .replace("\\n", " ")
        .replace("&amp;", "&")
        .replace("&nbsp;", " ")
        .replace("&#39;", "'")
        .replace("&quot;", '"')
    )
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", text))


def extract_trial(text: str) -> dict | None:
    counts: dict[int, int] = {}
    phrases: dict[int, str] = {}
    for pattern in TRIAL_PATTERNS:
        for match in pattern.finditer(text):
            days = to_days(match.group(1), match.group(2))
            if not days or not 1 <= days <= MAX_DAYS:
                continue
            phrase = match.group(0).strip()
            if days > LONG_OFFER_DAYS and not re.search(r"trial|пробн", phrase, re.I):
                continue
            counts[days] = counts.get(days, 0) + 1
            phrases.setdefault(days, phrase)
    if not counts:
        return None
    # Most repeated wins; ties go to the shortest — overstating is the worse error.
    days = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))[0][0]
    return {"days": days, "phrase": phrases[days], "occurrences": counts[days]}


def plural(value: int, one: str, few: str, many: str) -> str:
    if value % 100 in (11, 12, 13, 14):
        return many
    return {1: one, 2: few, 3: few, 4: few}.get(value % 10, many)


def humanize(days: int) -> str:
    if days >= 30 and days % 30 == 0:
        months = days // 30
        return f"{months} {plural(months, 'месяц', 'месяца', 'месяцев')} бесплатно"
    return f"{days} {plural(days, 'день', 'дня', 'дней')} бесплатно"


def collect(service: dict) -> dict:
    """Play metadata + a trial probe for one service."""
    pkg = service["package_name"]
    listing = fetch(f"https://play.google.com/store/apps/details?id={pkg}&hl=ru", "ru-RU,ru;q=0.9,en;q=0.8")

    title = re.search(r'property="og:title"[^>]*content="([^"]+)"', listing)
    description = re.search(r'name="description"[^>]*content="([^"]+)"', listing)
    icon = re.search(r'property="og:image"[^>]*content="([^"]+)"', listing)

    out = dict(service)
    out["exists"] = bool(title and icon)
    if not out["exists"]:
        return out

    out["app_name"] = TITLE_PREFIX.sub("", html.unescape(title.group(1))).strip()
    out["icon_url"] = icon.group(1)
    if description:
        text = re.sub(r"\s+", " ", html.unescape(description.group(1))).strip()
        out["description"] = (text if text.endswith((".", "!", "?")) else text + ".")[:160]
    else:
        out["description"] = ""

    parts = urlsplit(service["pricing_url"])
    root = f"{parts.scheme}://{parts.netloc}"
    for url in dict.fromkeys(
        [service["pricing_url"], f"{root}/pricing", f"{root}/plans", f"{root}/premium"]
    ):
        page = fetch(url)
        if len(page) < 500:
            continue
        found = extract_trial(flatten(page))
        if found:
            out["trial"] = found
            out["trial_url"] = url
            break
    return out


def build(results: list[dict], previous: dict, today: str) -> collections.OrderedDict:
    by_package = {r["package_name"]: r for r in results if r.get("exists")}
    deals: list[dict] = []
    seen: set[str] = set()

    # Existing entries keep their hand-written copy; the probe only overwrites the
    # trial terms it can actually confirm.
    for deal in previous.get("deals", []):
        pkg = deal["package_name"]
        seen.add(pkg)
        found = by_package.get(pkg)
        if found and found.get("icon_url"):
            deal["icon_url"] = found["icon_url"]
        if found and found.get("trial") and deal["type"] == "trial":
            trial = found["trial"]
            deal.update(
                title=humanize(trial["days"]),
                duration=humanize(trial["days"]).removesuffix(" бесплатно"),
                deep_link=found["trial_url"],
                last_verified_date=today,
                verified_by="auto",
                evidence=trial["phrase"],
                evidence_url=found["trial_url"],
            )
        else:
            deal["verified_by"] = deal.get("verified_by", "manual")
        deals.append(deal)

    for found in results:
        pkg = found["package_name"]
        if pkg in seen or not found.get("trial"):
            continue
        seen.add(pkg)
        trial = found["trial"]
        deals.append(
            collections.OrderedDict(
                [
                    ("id", pkg.replace(".", "-") + "-trial"),
                    ("package_name", pkg),
                    ("app_name", found["app_name"]),
                    ("title", humanize(trial["days"])),
                    ("type", "trial"),
                    ("duration", humanize(trial["days"]).removesuffix(" бесплатно")),
                    ("description", found["description"]),
                    ("price_after", ""),
                    ("deep_link", found["trial_url"]),
                    ("last_verified_date", today),
                    ("verified_by", "auto"),
                    ("evidence", trial["phrase"]),
                    ("evidence_url", found["trial_url"]),
                    ("brand_color", found["brand_color"]),
                    ("icon_url", found["icon_url"]),
                    ("glyph", found["glyph"]),
                    ("popularity", 70),
                ]
            )
        )

    watchlist = [
        collections.OrderedDict(
            [
                ("package_name", r["package_name"]),
                ("app_name", r["app_name"]),
                ("description", r["description"]),
                ("pricing_url", r["pricing_url"]),
                ("brand_color", r["brand_color"]),
                ("icon_url", r["icon_url"]),
                ("glyph", r["glyph"]),
                ("popularity", 60),
            ]
        )
        for r in results
        if r.get("exists") and r["package_name"] not in seen
    ]

    catalog = collections.OrderedDict()
    catalog["version"] = 2
    catalog["generated_at"] = today
    catalog["notice"] = (
        "Условия акций отличаются по регионам и меняются без предупреждения. "
        "Записи с verified_by=auto подтверждены чтением страницы тарифов сервиса "
        "на дату last_verified_date, остальные проверены вручную. "
        "Всегда сверяйтесь с сайтом сервиса перед оплатой."
    )
    catalog["deals"] = deals
    catalog["watchlist"] = watchlist
    return catalog


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jobs", type=int, default=8, help="parallel fetches")
    parser.add_argument("--out", type=Path, default=CATALOG)
    args = parser.parse_args()

    services = json.loads(SERVICES.read_text(encoding="utf-8"))
    # Always merge on top of the committed catalog, never on top of --out: hand-kept
    # entries live only there, and a dry run to another path must not drop them.
    previous = (
        json.loads(CATALOG.read_text(encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
        if CATALOG.exists()
        else {}
    )
    today = datetime.date.today().isoformat()

    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        results = list(pool.map(collect, services))

    missing = [r["package_name"] for r in results if not r.get("exists")]
    for pkg in missing:
        print(f"  not on Play, skipped: {pkg}", file=sys.stderr)

    catalog = build(results, previous, today)
    payload = json.dumps(catalog, ensure_ascii=False, indent=2) + "\n"
    args.out.write_text(payload, encoding="utf-8")
    # The APK ships a copy so the first launch works offline.
    if args.out == CATALOG:
        BUNDLED.write_text(payload, encoding="utf-8")

    deals = catalog["deals"]
    print(
        f"services checked: {len(services)}  offers: {len(deals)} "
        f"(auto-confirmed: {sum(1 for d in deals if d.get('verified_by') == 'auto')})  "
        f"watchlist: {len(catalog['watchlist'])}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
