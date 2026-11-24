"""Turns the JaCoCo CSV report into a Markdown table for the GitHub job summary."""
import csv
import sys

COUNTERS = ("LINE", "BRANCH", "INSTRUCTION")


def main(path):
    with open(path, newline="") as report:
        rows = [r for r in csv.DictReader(report) if r["CLASS"] != "FeatureFlagsApplication"]
    print("### Coverage\n")
    print("| counter | covered | missed | % |")
    print("|---|---:|---:|---:|")
    for counter in COUNTERS:
        missed = sum(int(r[counter + "_MISSED"]) for r in rows)
        covered = sum(int(r[counter + "_COVERED"]) for r in rows)
        total = missed + covered
        percent = 100.0 * covered / total if total else 100.0
        print(f"| {counter.lower()} | {covered} | {missed} | {percent:.1f} |")


if __name__ == "__main__":
    main(sys.argv[1])
