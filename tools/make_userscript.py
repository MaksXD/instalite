#!/usr/bin/env python3
"""Собирает версию для iPhone: docs/instalite.user.js из assets/inject.js.
Скрипт ставится в Safari через бесплатное расширение Userscripts."""
import os, re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
manifest = open(os.path.join(ROOT, "AndroidManifest.xml"), encoding="utf-8").read()
version = re.search(r'android:versionName="([^"]+)"', manifest).group(1)
body = open(os.path.join(ROOT, "assets", "inject.js"), encoding="utf-8").read()

header = f"""// ==UserScript==
// @name         InstaLite
// @description  Instagram без Reels и рекомендаций: лента подписок, скрыты Reels, «Интересное» и «Рекомендации для вас».
// @version      {version}
// @author       MaksXD
// @homepageURL  https://github.com/MaksXD/instalite
// @updateURL    https://maksxd.github.io/instalite/instalite.user.js
// @downloadURL  https://maksxd.github.io/instalite/instalite.user.js
// @match        https://www.instagram.com/*
// @match        https://instagram.com/*
// @run-at       document-start
// @inject-into  auto
// @noframes
// @grant        none
// ==/UserScript==

"""
out = os.path.join(ROOT, "docs", "instalite.user.js")
open(out, "w", encoding="utf-8").write(header + body)
print("written", out, len(header + body), "bytes")
