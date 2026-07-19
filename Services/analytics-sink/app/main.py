"""analytics-sink: consume trip-events, batch-INSERT into analytics.ride_events.

Full implementation is Task 18 — scheduled strictly after M2 is green.
"""
import asyncio


async def run() -> None:
    raise NotImplementedError("Task 18: aiokafka consumer -> ON CONFLICT DO NOTHING batch insert")


if __name__ == "__main__":
    asyncio.run(run())
