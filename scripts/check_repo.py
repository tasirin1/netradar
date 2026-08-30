#!/usr/bin/env python3
"""Ringkasan pemeriksaan repo agar tetap konsisten dengan pola NetRadar.

Gunakan:
    python3 scripts/check_repo.py           # cek berkas
    python3 scripts/check_repo.py --readme   # sinkron heading README.md & README.en.md
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def headings_from_markdown(path: Path) -> list[str]:
    text = path.read_text()
    inside_fence = False
    headings: list[str] = []
    for line in text.splitlines():
        if line.strip().startswith("```"):
            inside_fence = not inside_fence
            continue
        if not inside_fence and re.match(r'^#{1,6}\s+', line):
            headings.append(re.sub(r'\s+$', '', line))
    return headings


def sync_readme() -> bool:
    ok = True
    for required in ("README.md", "README.en.md"):
        if not (ROOT / required).exists():
            print(f"::error::{required} tidak ditemukan")
            ok = False
    if not ok:
        return False
    id_headings = headings_from_markdown(ROOT / "README.md")
    en_headings = headings_from_markdown(ROOT / "README.en.md")
    if id_headings != en_headings:
        print("::error::Struktur heading README.md dan README.en.md harus identik")
        print("ID:", id_headings)
        print("EN:", en_headings)
        return False
    print(f"✅ Heading sinkron ({len(id_headings)} heading)")
    return True


def secrets_guard() -> bool:
    warn_patterns = [
        "KbocH4Pl8Ef1zmDuWjTZUSVr",
        "P@ssw0rd1234!",
    ]
    bad_files = []
    exts = {".kt", ".kts", ".yml", ".yaml", ".md", ".py"}
    for path in ROOT.rglob("*"):
        if not path.is_file() or ".git" in path.parts or path.relative_to(ROOT).as_posix().startswith("scripts/"):
            continue
        if path.suffix not in exts:
            continue
        try:
            text = path.read_text()
        except UnicodeDecodeError:
            continue
        for pattern in warn_patterns:
            if pattern in text:
                bad_files.append(f"{path}: {pattern}")
    if bad_files:
        for msg in bad_files:
            print(f"::warning::{msg}")
    return not bad_files


def gradle_guard() -> bool:
    build = ROOT / "app" / "build.gradle.kts"
    if not build.exists():
        print("::error::app/build.gradle.kts tidak ditemukan")
        return False
    text = build.read_text()
    ok = True
    if 'minSdk = 21' not in text:
        print("::error::minSdk harus tetap 21")
        ok = False
    if 'versionName = "2.0"' not in text:
        print("::error::versionName harus tetap \"2.0\"")
        ok = False
    if 'lint {' not in text:
        print("::error::Blok lint { abortOnError = true } harus ada")
        ok = False
    return ok


def agents_guard() -> bool:
    agents = ROOT / "AGENTS.md"
    if not agents.exists():
        print("::error::AGENTS.md tidak ditemukan")
        return False
    text = agents.read_text()
    required_sections = ["Keputusan historis", "Pola bug & guard"]
    ok = True
    for section in required_sections:
        if section not in text:
            print(f"::error::Bagian '{section}' harus ada di AGENTS.md")
            ok = False
    return ok


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--readme", action="store_true")
    args = parser.parse_args()
    results: list[bool] = []
    if args.readme:
        results.append(sync_readme())
    else:
        results.append(secrets_guard())
        results.append(gradle_guard())
        results.append(agents_guard())
    if not all(results):
        raise SystemExit(1)
    print("✅ Repo guard passed")

if __name__ == "__main__":
    main()
