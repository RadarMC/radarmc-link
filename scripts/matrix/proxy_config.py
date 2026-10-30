#!/usr/bin/env python3
"""Points a generated Velocity or BungeeCord config at the matrix backend, offline mode, legacy forwarding.

usage: proxy_config.py VELOCITY|BUNGEECORD <config file> <backend host:port>
"""
import re
import sys

kind, path, backend = sys.argv[1:4]
with open(path, encoding="utf-8") as f:
    text = f.read()
if kind == "VELOCITY":
    text = re.sub(r'(?m)^online-mode\s*=.*$', 'online-mode = false', text)
    text = re.sub(r'(?m)^player-info-forwarding-mode\s*=.*$', 'player-info-forwarding-mode = "legacy"', text)
    text = re.sub(r'(?ms)^\[servers\].*?(?=^\[forced-hosts\])',
                  '[servers]\nlobby = "%s"\ntry = ["lobby"]\n\n' % backend, text)
    text = re.sub(r'(?ms)^\[forced-hosts\].*?(?=^\[advanced\])', '[forced-hosts]\n\n', text)
else:
    text = re.sub(r'(?m)^online_mode:.*$', 'online_mode: false', text)
    text = re.sub(r'(?m)^ip_forward:.*$', 'ip_forward: true', text)
    text = re.sub(r'(?m)^(\s*)address: localhost:25565', r'\1address: ' + backend, text)
with open(path, "w", encoding="utf-8") as f:
    f.write(text)
