from app.models.driver_status import DriverStatus
from app.models.location import LocationPing
from app.repositries.driver_status_repository import DriverStatusRepository
from app.repositries.location_repository import LocationRepository


class LocationService:
    def __init__(
        self,
        location_repository: LocationRepository,
        status_repository: DriverStatusRepository,
    ) -> None:
        self._location_repository = location_repository
        self._status_repository = status_repository

    async def mark_connected(self, driver_id: str) -> DriverStatus:
        return await self._status_repository.touch_alive(driver_id)

    async def handle_ping(self, driver_id: str, location: LocationPing) -> DriverStatus:
        await self._location_repository.write_and_publish(driver_id, location)
        return await self._status_repository.touch_alive(driver_id)

    async def mark_disconnected(self, driver_id: str) -> None:
        await self._location_repository.remove_driver(driver_id)
        await self._status_repository.delete_status(driver_id)
