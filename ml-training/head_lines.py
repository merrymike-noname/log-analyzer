"""
Copy first N lines from a log file into a new file in the same directory.

Usage:
    python head_lines.py --input datasets/Windows.log --lines 1000000
    -> creates datasets/Windows_1000000.log
"""
import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--lines", required=True, type=int)
    args = parser.parse_args()

    src = args.input
    if not src.exists():
        print(f"File not found: {src}")
        return

    # output: same dir, name with _N suffix
    dst = src.with_name(f"{src.stem}_{args.lines}{src.suffix}")

    written = 0
    with src.open("r", encoding="utf-8", errors="replace") as fin, \
            dst.open("w", encoding="utf-8", newline="") as fout:
        for line in fin:
            fout.write(line)
            written += 1
            if written >= args.lines:
                break

    print(f"Copied {written} lines from {src} -> {dst}")


if __name__ == "__main__":
    main()