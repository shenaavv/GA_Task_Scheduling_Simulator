#!/usr/bin/env python3
"""Convert 100 task records from the official Google Cluster Trace v1."""

import csv
import gzip
import math
from pathlib import Path


DATASET_DIR = Path(__file__).resolve().parent
SOURCE = DATASET_DIR / "google-cluster-data-1.csv.gz"
OUTPUT = DATASET_DIR / "tasks.csv"
TASK_LIMIT = 100


def convert_row(row, task_id):
    normalized_cores = float(row["NrmlTaskCores"])
    normalized_memory = float(row["NrmlTaskMem"])

    # The v1 trace exposes normalized demand, not MI or absolute RAM.
    pes = max(1, min(4, math.ceil(normalized_cores * 32)))
    length_mi = max(1, round(normalized_cores * 1_000_000))
    ram_mb = max(1024, round(normalized_memory * 64 * 1024))

    return {
        "taskId": task_id,
        "lengthMI": length_mi,
        "pes": pes,
        "ramMB": ram_mb,
        "priority": int(row["JobType"]),
    }


def main():
    selected = []
    seen_task_ids = set()

    with gzip.open(SOURCE, "rt", newline="") as source_file:
        reader = csv.DictReader(source_file, delimiter=" ")
        for row in reader:
            source_task_id = row["TaskID"]
            if source_task_id in seen_task_ids:
                continue
            if float(row["NrmlTaskCores"]) <= 0:
                continue

            seen_task_ids.add(source_task_id)
            selected.append(convert_row(row, len(selected) + 1))
            if len(selected) == TASK_LIMIT:
                break

    if len(selected) != TASK_LIMIT:
        raise RuntimeError(f"Only found {len(selected)} usable unique tasks")

    with OUTPUT.open("w", newline="") as output_file:
        writer = csv.DictWriter(
            output_file,
            fieldnames=["taskId", "lengthMI", "pes", "ramMB", "priority"],
        )
        writer.writeheader()
        writer.writerows(selected)

    print(f"Wrote {len(selected)} trace-derived tasks to {OUTPUT}")


if __name__ == "__main__":
    main()