import json

import jwt
from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from app.config import JWT_ALGORITHMS, JWT_SECRET
from app.models.location import LocationPing

router = APIRouter()


def _claims_from_token(token: str | None) -> dict | None:
    if not token or not JWT_SECRET:
        return None
    try:
        return jwt.decode(token, JWT_SECRET, algorithms=JWT_ALGORITHMS)
    except jwt.PyJWTError:
        return None


@router.websocket("/ws/location")
async def location_socket(ws: WebSocket) -> None:
    await ws.accept()

    claims = _claims_from_token(ws.query_params.get("token"))
    if not claims:
        await ws.close(code=4401)
        return

    role = str(claims.get("role", "")).upper()
    driver_id = claims.get("sub")
    if role != "DRIVER" or not driver_id:
        await ws.close(code=4403)
        return

    location_service = ws.app.state.location_service
    await location_service.mark_connected(driver_id)

    try:
        while True:
            raw = await ws.receive_text()
            payload = json.loads(raw)
            ping = LocationPing.model_validate(payload)
            await location_service.handle_ping(driver_id, ping)
    except (WebSocketDisconnect, json.JSONDecodeError, ValueError):
        pass
    finally:
        await location_service.mark_disconnected(driver_id)


@router.websocket("/ws/track/{driver_id}")
async def track_socket(ws: WebSocket, driver_id: str) -> None:
    await ws.accept()

    claims = _claims_from_token(ws.query_params.get("token"))
    if not claims:
        await ws.close(code=4401)
        return

    rider_id = claims.get("sub")
    if not rider_id:
        await ws.close(code=4403)
        return

    tracking_service = ws.app.state.tracking_service
    try:
        async for data in tracking_service.stream(driver_id):
            await ws.send_text(data)
    except WebSocketDisconnect:
        pass
