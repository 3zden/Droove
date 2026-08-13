def driver_status_key(driver_id: str) -> str:
	return f"driver:{driver_id}:status"


def driver_position_channel(driver_id: str) -> str:
	return f"driver:pos:{driver_id}"
