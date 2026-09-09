#!/usr/bin/env python3
"""Fail the build only on PMD/Checkstyle violations that fall on lines changed
vs a base git ref (a 'ratchet': old violations are grandfathered, new/touched
code must comply). Mirrors the pattern already used by Spotless's
ratchetFrom in this repo.

A violation on a changed line is only "new" if it didn't already exist, on
that same file, before the change - matched by (rule, message) rather than
line number, since a violation's line number shifts for reasons that have
nothing to do with the violation itself (e.g. a purely mechanical edit like
adding `final` to a method signature moves that declaration line into the
diff without changing whether it already lacked a Javadoc comment). Without
this, a backlog-only PR that touches thousands of declaration lines without
adding new content trips the ratchet on every one of them.

Matching is a per-file multiset (count), not a set: PMD/Checkstyle messages
for generic checks (e.g. "Comment is too large: Line too long") are
identical text for every occurrence, so a plain "does this (rule, message)
exist anywhere in the baseline" check would let a second, genuinely new
occurrence of the same generic message hide behind one pre-existing one
elsewhere in the file. Each baseline occurrence can grandfather at most one
head occurrence; anything beyond the baseline's count for that fingerprint
is new.

Usage: check-new-violations.py <base-ref> <head-pmd-glob> <head-checkstyle-glob> <base-pmd-glob> <base-checkstyle-glob>
Exits 1 (and prints the offending violations) if any violation is new on a
changed line; exits 0 otherwise.
"""
import glob
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import Counter

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


def baseline_pmd_fingerprints(pattern, changed):
    """{changed_file: Counter{(rule, message): count}} from the baseline (pre-change) PMD
    report - every violation that already existed, regardless of line number."""
    fingerprints = {}
    for path in glob.glob(pattern, recursive=True):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for file_el in root.findall("p:file", PMD_NS):
            cf = matches_changed_file(file_el.get("name", ""), changed)
            if cf is None:
                continue
            bucket = fingerprints.setdefault(cf, Counter())
            for v in file_el.findall("p:violation", PMD_NS):
                bucket[(v.get("rule"), v.text.strip())] += 1
    return fingerprints


def baseline_checkstyle_fingerprints(pattern, changed):
    """Same as baseline_pmd_fingerprints, for a Checkstyle report."""
    fingerprints = {}
    for path in glob.glob(pattern, recursive=True):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for file_el in root.findall("file"):
            cf = matches_changed_file(file_el.get("name", ""), changed)
            if cf is None:
                continue
            bucket = fingerprints.setdefault(cf, Counter())
            for e in file_el.findall("error"):
                rule = e.get("source", "").rsplit(".", 1)[-1]
                bucket[(rule, e.get("message", ""))] += 1
    return fingerprints


def check_pmd(pattern, changed, baseline):
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
            remaining = baseline.get(cf, Counter()).copy()
            # Consume baseline credit against ALL of head's violations for this file, in line
            # order, not just the changed-line ones - an unchanged-line occurrence must claim
            # its baseline credit first, so it can't be left over to wrongly excuse a distinct,
            # genuinely new occurrence of the same generic message elsewhere in the file.
            violations = sorted(
                file_el.findall("p:violation", PMD_NS), key=lambda v: int(v.get("beginline", "0"))
            )
            for v in violations:
                line = int(v.get("beginline", "0"))
                rule, msg = v.get("rule"), v.text.strip()
                if remaining[(rule, msg)] > 0:
                    remaining[(rule, msg)] -= 1
                elif line in changed[cf]:
                    offenders.append((cf, line, rule, msg))
    return offenders


def check_checkstyle(pattern, changed, baseline):
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
            remaining = baseline.get(cf, Counter()).copy()
            # See the matching comment in check_pmd: consume baseline credit against ALL of
            # head's errors for this file, not just the changed-line ones.
            errors = sorted(file_el.findall("error"), key=lambda e: int(e.get("line", "0")))
            for e in errors:
                line = int(e.get("line", "0"))
                rule = e.get("source", "").rsplit(".", 1)[-1]
                msg = e.get("message", "")
                if remaining[(rule, msg)] > 0:
                    remaining[(rule, msg)] -= 1
                elif line in changed[cf]:
                    offenders.append((cf, line, rule, msg))
    return offenders


def main():
    if len(sys.argv) != 6:
        print(
            "usage: check-new-violations.py <base-ref> <head-pmd-glob> <head-checkstyle-glob> "
            "<base-pmd-glob> <base-checkstyle-glob>",
            file=sys.stderr,
        )
        sys.exit(2)

    base_ref, pmd_glob, cs_glob, base_pmd_glob, base_cs_glob = sys.argv[1:6]
    changed = changed_lines_by_file(base_ref)

    if not changed:
        print("No changed .java lines vs base ref - nothing to ratchet-check.")
        return

    pmd_baseline = baseline_pmd_fingerprints(base_pmd_glob, changed)
    cs_baseline = baseline_checkstyle_fingerprints(base_cs_glob, changed)

    pmd_offenders = check_pmd(pmd_glob, changed, pmd_baseline)
    cs_offenders = check_checkstyle(cs_glob, changed, cs_baseline)
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
