import json
from typing import AsyncIterator

from redis.asyncio import Redis

from app.models.location import DriverPositionMessage, LocationPing
from app.redis.keys import driver_position_channel


class LocationRepository:
    def __init__(self, redis_client: Redis, geo_key: str) -> None:
        self._redis = redis_client
        self._geo_key = geo_key

    async def upsert_position(self, driver_id: str, location: LocationPing) -> None:
        # Redis GEO expects longitude, latitude order.
        await self._redis.geoadd(self._geo_key, (location.lng, location.lat, driver_id))

    async def remove_driver(self, driver_id: str) -> None:
        await self._redis.zrem(self._geo_key, driver_id)

    async def publish_position(self, driver_id: str, location: LocationPing) -> None:
        message = DriverPositionMessage(
            driverId=driver_id,
            lat=location.lat,
            lng=location.lng,
            heading=location.heading,
            ts=location.ts,
        )
        await self._redis.publish(driver_position_channel(driver_id), message.model_dump_json())

    async def write_and_publish(self, driver_id: str, location: LocationPing) -> None:
        message = DriverPositionMessage(
            driverId=driver_id,
            lat=location.lat,
            lng=location.lng,
            heading=location.heading,
            ts=location.ts,
        )
        async with self._redis.pipeline(transaction=False) as pipe:
            pipe.geoadd(self._geo_key, (location.lng, location.lat, driver_id))
            pipe.publish(driver_position_channel(driver_id), message.model_dump_json())
            await pipe.execute()

    async def stream_driver_positions(self, driver_id: str) -> AsyncIterator[str]:
        pubsub = self._redis.pubsub()
        channel = driver_position_channel(driver_id)
        await pubsub.subscribe(channel)
        try:
            async for message in pubsub.listen():
                if message.get("type") != "message":
                    continue
                data = message.get("data")
                if isinstance(data, str):
                    yield data
                else:
                    yield json.dumps(data)
        finally:
            await pubsub.unsubscribe(channel)
            await pubsub.aclose()
