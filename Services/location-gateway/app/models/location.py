from pydantic import BaseModel, Field


class LocationPing(BaseModel):
    lat: float = Field(ge=-90, le=90)
    lng: float = Field(ge=-180, le=180)
    heading: float | None = Field(default=None, ge=0, le=360)
    ts: str | None = None


class DriverPositionMessage(BaseModel):
    driverId: str
    lat: float
    lng: float
    heading: float | None = None
    ts: str | None = None
