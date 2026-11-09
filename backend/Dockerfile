FROM python:3.11-slim
ENV PYTHONUNBUFFERED=1 PORT=8080
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd --uid 10001 --create-home appuser
COPY . ./backend
# GitHub builds omit the generated index. Retrieval reports unavailable without it.
RUN if [ -f /app/backend/triage/references.sqlite3 ]; then chmod 0444 /app/backend/triage/references.sqlite3; fi
USER appuser
EXPOSE 8080
CMD ["sh", "-c", "exec uvicorn backend.app:app --host 0.0.0.0 --port ${PORT:-8080} --no-access-log"]
