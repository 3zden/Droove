"""location-gateway: GPS ingest + live tracking over WebSockets (port 8201)."""
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.websockets import router as websocket_router
from app.config import GEO_KEY, JWT_SECRET, REDIS_URL, STATUS_TTL_SECONDS
from app.redis.client import build_redis_client
from app.repositries.driver_status_repository import DriverStatusRepository
from app.repositries.location_repository import LocationRepository
from app.services.location_service import LocationService
from app.services.tracking_service import TrackingService


@asynccontextmanager
async def lifespan(app: FastAPI):
    if not JWT_SECRET:
        raise RuntimeError("JWT_SECRET must be set for location-gateway")

    redis_client = build_redis_client(REDIS_URL)

    location_repository = LocationRepository(redis_client=redis_client, geo_key=GEO_KEY)
    driver_status_repository = DriverStatusRepository(
        redis_client=redis_client,
        status_ttl_seconds=STATUS_TTL_SECONDS,
    )

    app.state.location_service = LocationService(
        location_repository=location_repository,
        status_repository=driver_status_repository,
    )
    app.state.tracking_service = TrackingService(location_repository=location_repository)

    yield

    await redis_client.aclose()


app = FastAPI(title="location-gateway", lifespan=lifespan)
app.include_router(websocket_router)


@app.get("/health")
async def health():
    return {"status": "ok"}


