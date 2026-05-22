# ml-training

Offline training pipeline for the Isolation Forest model used by `ml-worker`.

This is **not** part of the Docker Compose stack. The model is trained locally,
then the resulting `.pkl` file is copied into `ml-worker/models/` and packaged
into the worker image during build.

## Setup

```bash
cd ml-training
python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

## Dataset

Download Loghub Windows from Zenodo:
https://zenodo.org/records/8196385

Look for `Windows.tar.gz` (~3 GB compressed, ~27 GB unpacked).
Extract `Windows.log` into `ml-training/datasets/`:

```
ml-training/datasets/Windows.log
```

For development / quick iteration you don't need the full file.
Trim it to the first few million lines:

```bash
head -n 1000000 datasets/Windows.log > datasets/Windows_1M.log
```

## Run training

Full dataset:
```bash
python train.py --input datasets/Windows.log \
                --output ../ml-worker/models/isolation_forest.pkl
```

Subset for quick iteration:
```bash
python train.py --input datasets/Windows_1M.log \
                --output ../ml-worker/models/isolation_forest.pkl \
                --limit 1000000
```

## Parameters

| Flag             | Default | Meaning                                                    |
|------------------|---------|------------------------------------------------------------|
| `--input`        | —       | Path to raw `.log` file                                    |
| `--output`       | —       | Where to save the `.pkl`                                   |
| `--limit`        | None    | Cap on number of lines processed (useful for quick tests)  |
| `--contamination`| 0.01    | Expected proportion of anomalies in training data          |

## After training

Build and start the stack:
```bash
cd ..
docker compose up -d --build ml-worker
```

The worker will load the new model on startup.