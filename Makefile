.PHONY: up down logs smoke simulate

up:
	docker compose -f Docker/docker-compose.yml --env-file .env up -d

down:
	docker compose -f Docker/docker-compose.yml down

logs:
	docker compose -f Docker/docker-compose.yml logs -f

smoke:
	bash scripts/smoke.sh

simulate:
	bash scripts/simulator/run.sh
