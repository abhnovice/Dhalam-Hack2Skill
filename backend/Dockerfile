FROM python:3.11-slim
ENV PYTHONUNBUFFERED=1 PORT=8080
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd --uid 10001 --create-home appuser

COPY . ./backend

# 💡 FIX: Changed permissions from 0444 to 0644 so sqlite-vec can run
RUN if [ -f /app/backend/triage/references.sqlite3 ]; then chmod 0644 /app/backend/triage/references.sqlite3; fi

USER appuser
EXPOSE 8080
CMD ["sh", "-c", "exec uvicorn backend.app:app --host 0.0.0.0 --port ${PORT:-8080} --no-access-log"]
