from redis.asyncio import Redis, from_url


def build_redis_client(redis_url: str) -> Redis:
	return from_url(redis_url, decode_responses=True)
