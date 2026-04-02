# Contributing

## Development Setup

1. Clone the repository
2. Copy `.env.example` to `.env` and adjust values
3. Start PostgreSQL: `docker compose up postgresql -d`
4. Run in dev mode: `./gradlew quarkusDev`
5. Frontend: `cd frontend/ldm-frontend && npm install && npm run dev`

## Code Style

- Java 17, follow existing patterns
- Use Lombok annotations where appropriate
- MapStruct for DTO mapping
- Protobuf for inter-LDM serialization

## Testing

```bash
./gradlew test
cd frontend/ldm-frontend && npx next build
```

## Commit Convention

Use conventional commits: `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`
