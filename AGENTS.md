# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java/de/tum/cit/aet/openapi/` contains the generator implementation (entry point: `Angular22Generator.java`).
- `src/main/resources/angular22/` holds Mustache templates used for generated Angular output (`model.mustache`, `api-service.mustache`, `api-resource.mustache`, etc.).
- `src/main/resources/META-INF/services/` registers the generator for the OpenAPI SPI.
- `example/` includes a sample OpenAPI spec (`example/example-openapi.yaml`) for manual checks.
- `build/` is Gradle output (ignore for source edits).

## Build, Test, and Development Commands
- `./gradlew build` — compiles, runs tests, and produces JARs.
- `./gradlew test` — runs JUnit tests (if present).
- `./gradlew publishToMavenLocal` — publishes the generator to your local Maven repo for quick integration testing.
- `./gradlew publish` — publishes to GitHub Packages (requires `GITHUB_ACTOR`/`GITHUB_TOKEN`).

## Coding Style & Naming Conventions
- Java 25 source; follow standard Java style (4-space indentation, braces on the same line).
- Keep generator options as `UPPER_SNAKE_CASE` constants in `Angular22Generator.java`.
- Template outputs follow naming patterns defined in the generator: models use `.ts`, APIs use `*-api.ts`, resources use `*-resources.ts`.
- Mustache templates should stay minimal; prefer code in Java when logic is complex.

## Testing Guidelines
- Tests live in `src/test/java` (`*Test.java`); each OpenAPI fixture they generate from lives in `src/test/resources/fixtures`. A spec that must fail generation lives in `src/test/resources/invalid`, since `GeneratedCodeCompilesTest` generates every fixture.
- `GeneratedCodeCompilesTest` type-checks the generated code of every fixture with the TypeScript compiler and Angular types pinned in `src/test/typescript`. `./gradlew test` runs `npm ci` there first, so it needs Node.js and npm.
- Run `./gradlew test` before submitting changes that affect code generation.

## Commit & Pull Request Guidelines
- The Git history currently contains only an “Initial commit,” so no formal commit convention exists yet.
- Use concise, imperative commit messages (e.g., “Add template option for X”).
- PRs should include a short description, the rationale, and how you validated changes (commands or manual checks). If generated output changes, mention the impacted files or templates.

## Architecture Overview
- `Angular22Generator` extends the OpenAPI TypeScript Angular generator and swaps template mappings to the files in `src/main/resources/angular22/`.
- CLI config options are wired through `processOpts()` and then exposed to templates via `additionalProperties`.
- Generation outputs are routed to `generated-code/angular22/` by default and follow the naming patterns set in the generator.

## Configuration Tips
- The generator is registered via `src/main/resources/META-INF/services/org.openapitools.codegen.CodegenConfig`—update it only if the main class name changes.
- Publishing to GitHub Packages requires environment credentials; avoid hardcoding tokens in Gradle.
