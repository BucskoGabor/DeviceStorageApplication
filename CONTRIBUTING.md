# Contributing

This is a single-developer project (developed by an AI agent). The architecture and design decisions are documented in [`implementation_plan.md`](./implementation_plan.md), and the task tracking lives in [`agent_progress.md`](./agent_progress.md).

## Development Workflow

1. Pick the next task from `agent_progress.md` (Next 3 Task section)
2. Implement according to the design in `implementation_plan.md`
3. Self-review against the Definition of Done
4. Commit with a descriptive message referencing the task ID
5. Update the task status in `agent_progress.md` (e.g., `[ ]` → `[~]` → `[x]`)

## Coding Conventions

- **Backend:** Java 21, Spring Boot 3.3+, Maven, feature-based packages
- **Frontend:** React 18, TypeScript, Vite, Tailwind + shadcn/ui
- **Style:** `.editorconfig` enforced (4 spaces for Java, 2 for JS/TS)
- **Java formatting:** Google Java Format via Spotless (see CI pipeline)
- **Linting:** Checkstyle (Java), ESLint (TS, coming in Task 1.2/1.7)

## Security

- Never commit `.env`, `backup.env`, or any secrets — only `.env.example`
- API tokens, JWT secrets, and SMTP passwords are environment-injected
- See `implementation_plan.md` §3 for the security architecture

## Testing

- Unit tests: service layer (80% Jacoco coverage gate)
- Integration tests: Testcontainers PostgreSQL
- Smoke tests: `scripts/smoke-test.sh` after `docker-compose up`

## Questions or Issues

Open an issue on GitHub or check `docs/runbook.md` for common problems.