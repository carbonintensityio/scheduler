#!/usr/bin/env python3
"""Fail the build only on PMD/Checkstyle violations that fall on lines changed
vs a base git ref (a 'ratchet': old violations are grandfathered, new/touched
code must comply). Mirrors the pattern already used by Spotless's
ratchetFrom in this repo.

Usage: check-new-violations.py <base-ref> <pmd-glob> <checkstyle-glob>
Exits 1 (and prints the offending violations) if any violation lands on a
changed line; exits 0 otherwise.
"""
import glob
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

PMD_NS = {"p": "http://pmd.sourceforge.net/report/2.0.0"}

HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")


def changed_lines_by_file(base_ref):
    """Return {absolute_or_relative_path: set(changed line numbers)} from a
    zero-context diff against base_ref. Paths are as git reports them
    (relative to the repo root running the diff)."""
    out = subprocess.run(
        ["git", "diff", "-U0", f"{base_ref}...HEAD", "--", "*.java"],
        capture_output=True, text=True, check=True,
    ).stdout

    result = {}
    current_file = None
    for line in out.splitlines():
        if line.startswith("+++ "):
            path = line[4:]
            if path == "/dev/null":
                current_file = None
            else:
                # "+++ b/path/to/File.java" -> "path/to/File.java"
                current_file = path.split("/", 1)[1] if path.startswith("b/") else path
                result.setdefault(current_file, set())
        elif line.startswith("@@") and current_file is not None:
            m = HUNK_RE.match(line)
            if m:
                start = int(m.group(1))
                count = int(m.group(2)) if m.group(2) is not None else 1
                if count == 0:
                    # pure deletion hunk, nothing added -> no new lines to check
                    continue
                result[current_file].update(range(start, start + count))
    return result


def matches_changed_file(report_path, changed_files):
    """PMD/Checkstyle report paths are absolute; git diff paths are relative
    to the repo root. Match by suffix."""
    for cf in changed_files:
        if report_path.endswith(cf):
            return cf
    return None


def check_pmd(pattern, changed):
    offenders = []
    for path in glob.glob(pattern, recursive=True):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for file_el in root.findall("p:file", PMD_NS):
            fname = file_el.get("name", "")
            cf = matches_changed_file(fname, changed)
            if cf is None:
                continue
            for v in file_el.findall("p:violation", PMD_NS):
                line = int(v.get("beginline", "0"))
                if line in changed[cf]:
                    offenders.append((cf, line, v.get("rule"), v.text.strip()))
    return offenders


def check_checkstyle(pattern, changed):
    offenders = []
    for path in glob.glob(pattern, recursive=True):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for file_el in root.findall("file"):
            fname = file_el.get("name", "")
            cf = matches_changed_file(fname, changed)
            if cf is None:
                continue
            for e in file_el.findall("error"):
                line = int(e.get("line", "0"))
                if line in changed[cf]:
                    rule = e.get("source", "").rsplit(".", 1)[-1]
                    offenders.append((cf, line, rule, e.get("message", "")))
    return offenders


def main():
    if len(sys.argv) != 4:
        print("usage: check-new-violations.py <base-ref> <pmd-glob> <checkstyle-glob>", file=sys.stderr)
        sys.exit(2)

    base_ref, pmd_glob, cs_glob = sys.argv[1], sys.argv[2], sys.argv[3]
    changed = changed_lines_by_file(base_ref)

    if not changed:
        print("No changed .java lines vs base ref - nothing to ratchet-check.")
        return

    pmd_offenders = check_pmd(pmd_glob, changed)
    cs_offenders = check_checkstyle(cs_glob, changed)
    all_offenders = pmd_offenders + cs_offenders

    if not all_offenders:
        print(f"No PMD/Checkstyle violations on changed lines ({sum(len(v) for v in changed.values())} changed lines checked).")
        return

    print(f"::error::{len(all_offenders)} PMD/Checkstyle violation(s) on lines you touched:")
    for cf, line, rule, msg in sorted(all_offenders):
        print(f"  {cf}:{line} [{rule}] {msg}")
    sys.exit(1)


if __name__ == "__main__":
    main()
