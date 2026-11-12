# GuardPay AI service — FastAPI + OpenAI evidence analysis (port 8001).
FROM python:3.12-slim
WORKDIR /app
ENV PIP_DEFAULT_TIMEOUT=300 \
    PIP_RETRIES=20
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
EXPOSE 8001
# OPENAI_API_KEY comes from the environment at runtime — never baked in.
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
