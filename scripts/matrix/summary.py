#!/usr/bin/env python3
"""Prints the matrix results directory as a Markdown table. usage: summary.py <results dir>"""
import glob
import json
import os
import sys

out = sys.argv[1]
print("| Scenario | Server | Java | Enable OK | Heartbeat platform / software | Join seen | Reward ran | Result |")
print("|---|---|---|---|---|---|---|---|")
for path in sorted(glob.glob(os.path.join(out, "*.json"))):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for r in data["rows"]:
        print("| %s | %s | %s | %s | %s / %s | %s | %s | %s |" % (
            data["scenario"], r["server"], r["java"], "yes" if r["enable_ok"] else "NO", r["platform"], r["software"],
            r["join"], r["reward"], "pass" if r["ok"] else "FAIL"))
