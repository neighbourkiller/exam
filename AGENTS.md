# Repository Guidelines

## Project Structure & Module Organization
The backend is a Spring Boot 4 application under `src/main/java/com/ekusys/exam`, organized by domain (`auth`, `question`, `paper`, `exam`, `grading`, `analytics`, `admin`) with shared code in `common` and persistence types in `repository`. Tests live in `src/test/java/com/ekusys/exam` and follow the same package layout. Runtime config and SQL live in `src/main/resources`, including `application.yaml`, `schema.sql`, `data.sql`, and Flyway-style migrations in `db/migration`. The Vue 3 frontend is embedded in `src/main/resources/frontend`; keep app code in `src/`, and treat `dist/` and `node_modules/` as generated output.

## Build, Test, and Development Commands
Use the Maven wrapper from the repository root:

- `./mvnw spring-boot:run` starts the backend on port `8080`.
- `./mvnw test` runs the JUnit 5 and Spring test suite.
- `./mvnw package` builds the deployable jar.

For the frontend:

- `cd src/main/resources/frontend && npm install` installs Vite and Vue dependencies.
- `npm run dev` starts the frontend dev server on `5173`.
- `npm run build` produces the frontend bundle.

## Coding Style & Naming Conventions
Follow the existing style in each area: Java uses 4-space indentation, constructor injection, and package-by-feature organization. Class names use PascalCase; services, controllers, DTOs, entities, and mappers should keep the current suffix pattern such as `QuestionService`, `ExamController`, and `UserView`. Vue files use PascalCase component names like `LoginView.vue`; JavaScript modules use short lowercase names such as `auth.js` and `http.js`. No formatter or linter is configured, so keep imports tidy and match surrounding code instead of introducing a new style.

## Testing Guidelines
Backend tests use JUnit 5, Mockito, and Spring Boot test support. Add focused tests under the matching package in `src/test/java`, and name files `*Test.java`. Prefer small service-level tests for business rules and mapper/controller tests when behavior crosses boundaries. Frontend test tooling is not configured yet, so frontend changes should include a manual smoke check with `npm run dev`.

## Commit & Pull Request Guidelines
Recent commits use short, imperative summaries, often in Chinese, for example `登录界面美化` or `修复ID策略、新增试卷、查询试卷`. Keep commits scoped to one change set and describe user-visible behavior, not implementation trivia. Pull requests should state the affected module, list config or schema changes, include test evidence (`./mvnw test`, manual UI checks), and attach screenshots for frontend updates.

## Security & Configuration Tips
`src/main/resources/application.yaml` currently contains development-only database, Redis, JWT, and MinIO values. Do not treat them as production secrets; override sensitive settings with environment-specific configuration before deployment.
