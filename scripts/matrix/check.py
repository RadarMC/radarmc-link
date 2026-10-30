#!/usr/bin/env python3
"""Checks one RadarLink matrix scenario: what the stub API received and what the server logs say.

usage: check.py <out-dir> <scenario> [--wait SECONDS] --server NAME:LOGFILE:JAVA:ROLE ... [--reward-in NAME]

ROLE is server, backend or proxy. Exit code 0 when every check passed. Writes <out-dir>/<scenario>.json.
"""
import argparse
import json
import os
import re
import sys
import time

BOT = "RadarBot"
REWARD_MARK = "RADARLINK-REWARD " + BOT + " 3"
EXPECTED_WARNINGS = ("api-url uses plain http",)
LEVEL = re.compile(r"\b(WARN|WARNING|ERROR|SEVERE)\b")


def load_requests(out):
    path = os.path.join(out, "requests.jsonl")
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def read(path):
    if not os.path.exists(path):
        return ""
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def plugin_problems(log):
    """RadarLink warnings and errors, plus any stack trace that runs through RadarLink code."""
    problems = []
    for line in log.splitlines():
        low = line.lower()
        if "radarlink" in low and LEVEL.search(line) and not any(w in line for w in EXPECTED_WARNINGS):
            problems.append(line.strip())
        elif "at org.radarmc.link" in line:
            problems.append(line.strip())
    return problems


def proxy_acked(store):
    """The proxy completes a forwarded reward only after the backend's signed ack."""
    try:
        with open(store, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError):
        return False
    return not data.get("queued") and "matrix-vote-1" in data.get("handled", {})


def evaluate(out, servers, reward_in, proxy_store=None):
    requests = load_requests(out)
    beats = [r for r in requests if r["path"].endswith("/heartbeat") and r.get("body")]
    joins = [r for r in requests if r["path"].endswith("/joins") and r.get("body")]
    confirms = [r for r in requests if r["path"].endswith("/confirm")]
    instances = {}
    for b in beats:
        instances.setdefault(b["body"]["instance_id"], b["body"])
    rows = []
    ok = True
    for s in servers:
        log = read(s["log"])
        started = re.search(r"RadarLink \S+ started for matrix-test", log) is not None
        problems = plugin_problems(log)
        if s["role"] == "proxy":
            wanted = [b for b in instances.values() if b["platform"] in ("velocity", "bungeecord")]
        elif s["role"] == "backend":
            wanted = [b for b in instances.values() if b["platform"] not in ("velocity", "bungeecord") and b["behind_proxy"]]
        else:
            wanted = [b for b in instances.values() if not b["behind_proxy"]]
        beat = wanted[0] if wanted else None
        ua_ok = all(r["ua"].startswith("RadarLink/") for r in requests)
        stopping = beat is not None and any(
            b["body"]["instance_id"] == beat["instance_id"] and b["body"].get("stopping") for b in beats)
        if s["role"] == "backend":
            join = "n/a (proxy reports)"
            join_ok = not any(j["body"]["instance_id"] == (beat or {}).get("instance_id") for j in joins)
        else:
            mine = [j for j in joins if beat and j["body"]["instance_id"] == beat["instance_id"]]
            nicks = [e for j in mine for e in j["body"]["joins"] if e.get("nick") == BOT]
            join_ok = bool(nicks) and all(e.get("ip") and e.get("uuid") for e in nicks)
            join = "yes (" + nicks[0]["ip"] + ")" if join_ok else "no"
        if reward_in is None or reward_in == s["name"]:
            reward_ok = REWARD_MARK in log and bool(confirms)
            reward = "yes" if reward_ok else "no"
        else:
            reward_ok = True
            reward = "-"
            if s["role"] == "proxy":
                acked = proxy_store is not None and proxy_acked(proxy_store)
                reward = "forwarded, acked" if acked else "forwarded, no ack"
                reward_ok = acked or proxy_store is None
        row = {
            "server": s["name"],
            "java": s["java"],
            "enable_ok": started and not problems,
            "platform": beat["platform"] if beat else None,
            "software": beat["software"] if beat else None,
            "behind_proxy": beat["behind_proxy"] if beat else None,
            "rewards_mode": beat["rewards"] if beat else None,
            "stopping_heartbeat": stopping,
            "join": join,
            "reward": reward,
            "user_agent_ok": ua_ok,
            "problems": problems[:20],
        }
        row_ok = row["enable_ok"] and beat is not None and join_ok and reward_ok and ua_ok
        row["ok"] = row_ok
        ok = ok and row_ok
        rows.append(row)
    return ok, rows


def main():
    p = argparse.ArgumentParser()
    p.add_argument("out")
    p.add_argument("scenario")
    p.add_argument("--server", action="append", required=True)
    p.add_argument("--reward-in")
    p.add_argument("--proxy-store", help="the proxy's rewards.json; requires the forwarded reward to be acked")
    p.add_argument("--wait", type=int, default=0)
    p.add_argument("--quiet", action="store_true")
    a = p.parse_args()
    servers = []
    for spec in a.server:
        name, log, java, role = spec.split(":")
        servers.append({"name": name, "log": log, "java": java, "role": role})
    deadline = time.time() + a.wait
    while True:
        ok, rows = evaluate(a.out, servers, a.reward_in, a.proxy_store)
        if ok or time.time() >= deadline:
            break
        time.sleep(5)
    if not a.quiet:
        with open(os.path.join(a.out, a.scenario + ".json"), "w", encoding="utf-8") as f:
            json.dump({"scenario": a.scenario, "ok": ok, "rows": rows}, f, indent=2)
        for r in rows:
            print("%-8s %-28s java %-3s enable=%s platform=%s software=%r join=%s reward=%s stopping=%s%s" % (
                "PASS" if r["ok"] else "FAIL", a.scenario + "/" + r["server"], r["java"], r["enable_ok"], r["platform"],
                r["software"], r["join"], r["reward"], r["stopping_heartbeat"],
                "" if not r["problems"] else "\n    " + "\n    ".join(r["problems"])))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
