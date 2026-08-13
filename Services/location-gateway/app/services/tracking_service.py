from typing import AsyncIterator

from app.repositries.location_repository import LocationRepository


class TrackingService:
	def __init__(self, location_repository: LocationRepository) -> None:
		self._location_repository = location_repository

	async def stream(self, driver_id: str) -> AsyncIterator[str]:
		async for payload in self._location_repository.stream_driver_positions(driver_id):
			yield payload
