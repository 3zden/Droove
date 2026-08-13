from redis.asyncio import Redis

from app.models.driver_status import DriverStatus
from app.redis.keys import driver_status_key

# Extend TTL without clobbering BUSY. If status exists, only move expiry.
TOUCH_STATUS_SCRIPT = """
if redis.call('EXPIRE', KEYS[1], ARGV[2]) == 1 then
  return redis.call('GET', KEYS[1])
end
redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
return ARGV[1]
"""


class DriverStatusRepository:
    def __init__(self, redis_client: Redis, status_ttl_seconds: int) -> None:
        self._redis = redis_client
        self._status_ttl_seconds = status_ttl_seconds
        self._touch_script = self._redis.register_script(TOUCH_STATUS_SCRIPT)

    async def touch_alive(self, driver_id: str) -> DriverStatus:
        current = await self._touch_script(
            keys=[driver_status_key(driver_id)],
            args=[DriverStatus.AVAILABLE.value, self._status_ttl_seconds],
        )
        return DriverStatus(current)

    async def set_status(self, driver_id: str, status: DriverStatus) -> None:
        await self._redis.set(
            driver_status_key(driver_id),
            status.value,
            ex=self._status_ttl_seconds,
        )

    async def delete_status(self, driver_id: str) -> None:
        await self._redis.delete(driver_status_key(driver_id))

    async def ttl(self, driver_id: str) -> int:
        return await self._redis.ttl(driver_status_key(driver_id))
