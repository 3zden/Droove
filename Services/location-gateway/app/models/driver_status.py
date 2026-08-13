from enum import Enum


class DriverStatus(str, Enum):
	AVAILABLE = "AVAILABLE"
	BUSY = "BUSY"
	OFFLINE = "OFFLINE"
