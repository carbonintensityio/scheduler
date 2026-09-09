#!/usr/bin/env python3
"""Summarize PMD/Checkstyle/CPD XML reports into a Markdown job summary.

Usage: summarize-static-analysis.py <pmd-glob> <checkstyle-glob> <cpd-glob>
Globs are matched recursively (supports '**'), so callers can pass a plain
path (single-module repo) or a '**/target/...' pattern (multi-module reactor).
"""
import glob
import sys
import xml.etree.ElementTree as ET
from collections import Counter

PMD_NS = {"p": "http://pmd.sourceforge.net/report/2.0.0"}
CPD_NS = {"c": "https://pmd-code.org/schema/cpd-report"}


def summarize_pmd(pattern):
    rule_counts = Counter()
    total = 0
    for path in glob.glob(pattern, recursive=True):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for file_el in root.findall("p:file", PMD_NS):
            for v in file_el.findall("p:violation", PMD_NS):
                rule_counts[v.get("rule", "?")] += 1
                total += 1
    return total, rule_counts


def summarize_checkstyle(pattern):
    rule_counts = Counter()
    total = 0
    for path in glob.glob(pattern, recursive=True):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for file_el in root.findall("file"):
            for e in file_el.findall("error"):
                source = e.get("source", "")
                rule = source.rsplit(".", 1)[-1].replace("Check", "") or "?"
                rule_counts[rule] += 1
                total += 1
    return total, rule_counts


def summarize_cpd(pattern):
    dup_count = 0
    line_count = 0
    for path in glob.glob(pattern, recursive=True):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for dup in root.findall("c:duplication", CPD_NS):
            dup_count += 1
            line_count += int(dup.get("lines", 0) or 0)
    return dup_count, line_count


def top_table(counter, n=10):
    if not counter:
        return "_none_"
    lines = ["| Rule | Count |", "|---|---|"]
    for rule, count in counter.most_common(n):
        lines.append(f"| `{rule}` | {count} |")
    return "\n".join(lines)


def main():
    if len(sys.argv) != 4:
        print("usage: summarize-static-analysis.py <pmd-glob> <checkstyle-glob> <cpd-glob>", file=sys.stderr)
        sys.exit(1)

    pmd_total, pmd_rules = summarize_pmd(sys.argv[1])
    cs_total, cs_rules = summarize_checkstyle(sys.argv[2])
    cpd_dups, cpd_lines = summarize_cpd(sys.argv[3])

    print("## Static analysis summary")
    print()
    print(f"- **PMD**: {pmd_total} violations")
    print(f"- **Checkstyle**: {cs_total} violations")
    print(f"- **CPD**: {cpd_dups} duplicate blocks ({cpd_lines} lines)")
    print()
    print("<details><summary>Top PMD rules</summary>")
    print()
    print(top_table(pmd_rules))
    print()
    print("</details>")
    print()
    print("<details><summary>Top Checkstyle rules</summary>")
    print()
    print(top_table(cs_rules))
    print()
    print("</details>")
    print()
    print(
        "Full reports are attached as the `pmd-checkstyle-reports` build artifact. "
        "For a browsable local HTML report, run `./mvnw pmd:pmd pmd:cpd checkstyle:checkstyle` "
        "and open `target/reports/{pmd,cpd,checkstyle}.html`."
    )


if __name__ == "__main__":
    main()
