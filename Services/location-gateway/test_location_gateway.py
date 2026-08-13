import os

import jwt
import pytest
import redis.asyncio as redis

os.environ.setdefault("JWT_SECRET", "test-secret-at-least-32-bytes-long!!")

from app.config import STATUS_TTL_SECONDS
from app.models.driver_status import DriverStatus
from app.repositries.driver_status_repository import DriverStatusRepository
from app.repositries.location_repository import LocationRepository
from app.api.websockets import _claims_from_token


SECRET = os.environ["JWT_SECRET"]


def test_valid_token_decodes_claims():
    token = jwt.encode({"sub": "driver-42", "role": "DRIVER"}, SECRET, algorithm="HS256")
    claims = _claims_from_token(token)
    assert claims is not None
    assert claims["sub"] == "driver-42"


def test_missing_invalid_or_wrong_secret_token_is_rejected():
    assert _claims_from_token(None) is None
    assert _claims_from_token("not.a.jwt") is None

    forged = jwt.encode({"sub": "driver-42"}, "another-secret", algorithm="HS256")
    assert _claims_from_token(forged) is None


@pytest.fixture
async def r():
    client = redis.from_url("redis://localhost:6379/0", decode_responses=True)
    try:
        await client.ping()
    except Exception:
        pytest.skip("no Redis on localhost:6379")

    await client.delete("driver:test:status", "drivers:geo")
    yield client
    await client.delete("driver:test:status", "drivers:geo")
    await client.aclose()


@pytest.mark.asyncio
async def test_first_touch_sets_available_with_ttl(r):
    repo = DriverStatusRepository(redis_client=r, status_ttl_seconds=STATUS_TTL_SECONDS)
    status = await repo.touch_alive("test")
    assert status == DriverStatus.AVAILABLE
    assert await r.get("driver:test:status") == DriverStatus.AVAILABLE.value

    ttl = await repo.ttl("test")
    assert 0 < ttl <= STATUS_TTL_SECONDS


@pytest.mark.asyncio
async def test_touch_does_not_overwrite_busy_status(r):
    repo = DriverStatusRepository(redis_client=r, status_ttl_seconds=STATUS_TTL_SECONDS)
    await repo.set_status("test", DriverStatus.BUSY)

    status = await repo.touch_alive("test")

    assert status == DriverStatus.BUSY
    assert await r.get("driver:test:status") == DriverStatus.BUSY.value


@pytest.mark.asyncio
async def test_touch_extends_liveness_clock(r):
    repo = DriverStatusRepository(redis_client=r, status_ttl_seconds=STATUS_TTL_SECONDS)
    await r.set("driver:test:status", DriverStatus.AVAILABLE.value, ex=3)

    await repo.touch_alive("test")
    assert await repo.ttl("test") > 3


@pytest.mark.asyncio
async def test_geo_write_and_remove(r):
    location_repo = LocationRepository(redis_client=r, geo_key="drivers:geo")

    from app.models.location import LocationPing

    await location_repo.upsert_position("driver-1", LocationPing(lat=33.5731, lng=-7.5898))
    found = await r.geosearch(
        "drivers:geo",
        longitude=-7.5898,
        latitude=33.5731,
        radius=3,
        unit="km",
        sort="ASC",
    )
    assert "driver-1" in found

    await location_repo.remove_driver("driver-1")
    found_after = await r.geosearch(
        "drivers:geo",
        longitude=-7.5898,
        latitude=33.5731,
        radius=3,
        unit="km",
        sort="ASC",
    )
    assert "driver-1" not in found_after
