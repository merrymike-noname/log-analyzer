# ---------- builder stage ----------
FROM python:3.13-slim AS builder

WORKDIR /build

# Install deps into a separate prefix so we can copy them cleanly later
COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt


# ---------- runtime stage ----------
FROM python:3.13-slim AS runtime

WORKDIR /app

# Copy installed packages from builder
COPY --from=builder /install /usr/local

# Copy application code and the trained model
COPY src/ ./src/
COPY models/ ./models/

WORKDIR /app/src

# Run as non-root for better security
RUN useradd --create-home --shell /bin/bash worker \
    && chown -R worker:worker /app
USER worker

# Use exec form so the process receives SIGTERM properly (PID 1)
CMD ["python", "-u", "main.py"]