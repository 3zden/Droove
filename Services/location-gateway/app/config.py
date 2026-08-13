import os


REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/0")
JWT_SECRET = os.getenv("JWT_SECRET", "")
JWT_ALGORITHMS = ["HS256"]

GEO_KEY = "drivers:geo"
STATUS_TTL_SECONDS = int(os.getenv("LOCATION_STATUS_TTL_SECONDS", "15"))
