"""location-gateway: GPS ingest + live tracking over WebSockets (port 8201).

WS endpoints, JWT auth, and Redis GEO bodies land in Day 3 (Task 12 / M1).
"""
from fastapi import FastAPI

app = FastAPI(title="location-gateway")


@app.get("/health")
async def health():
    return {"status": "ok"}
