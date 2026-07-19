"""notification-service: Kafka -> WebSocket fanout (port 8202).

WS registry, consumer bodies, and dedupe land in Day 4 (Task 16 / M2b).
"""
from fastapi import FastAPI

app = FastAPI(title="notification-service")


@app.get("/health")
async def health():
    return {"status": "ok"}
